package com.nurseflow.roster.domain.readiness

import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.domain.model.WorkPattern
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * Setup readiness and pre-generation check (A18). It only reads settings and reports what is missing or likely
 * short; it never relaxes a rule. Only [ReadinessItem.blocking] items stop automatic generation.
 */
enum class ReadinessKey {
    SHIFT_TYPES, STAFF, TEAM_SENIORS, WORK_RULES, OFF_REQUEST_POLICY, TARGET_HOURS,
    REQUIREMENTS, HOLIDAY_CALENDAR, PENDING_OFF_REQUESTS,
}

enum class ReadinessStatus {
    /** Set and usable. */
    COMPLETE,
    /** Not entered by the admin: the shown default is used and is not the hospital's confirmed policy. */
    DEFAULT,
    /** Usable but likely to leave slots unfilled or to need review. Never blocks. */
    WARNING,
    /** Required and absent: automatic generation is rejected until it is set. */
    MISSING,
}

data class ReadinessItem(
    val key: ReadinessKey,
    val status: ReadinessStatus,
    val message: String,
    /** Short machine-readable names of what is missing (shift kinds, team ids, …); empty when nothing is. */
    val missing: List<String> = emptyList(),
) {
    val blocking: Boolean get() = status == ReadinessStatus.MISSING
}

enum class SeniorShortageReason {
    /** No rosterable senior in the team can take this shift kind at all. */
    NO_SENIOR,
    /**
     * The team needs more senior working days than its seniors have in the month under the consecutive-work
     * limit. A day's need is the most senior slots of that day running at the same time: one nurse may take
     * several slots of a day as long as they do not overlap, and they count as one working day.
     */
    MONTHLY_CAPACITY,
    /** At some moment more of the team's senior slots run at once than it has seniors (overlapping slots need different nurses). */
    SAME_DAY,
}

/** Demand and supply of one team's senior slots, for one shift kind or (kind = null) all kinds together. */
data class SeniorCoverage(
    val teamId: UUID,
    val teamName: String,
    val shiftKind: ShiftKind?,
    val requiredSeniorSlots: Int,
    val availableSeniors: Int,
    /** Upper bound of senior working days this month: seniors × [SetupReadinessEvaluator.maxShiftsPerNurse]. */
    val seniorShiftCapacity: Int,
    /** Fewest senior working days the slots need: per date, the most of its senior slots overlapping at once, summed. */
    val requiredSeniorDays: Int,
    val reasons: List<SeniorShortageReason>,
    /** For SAME_DAY: the date of the first slot at which overlapping senior slots exceed the seniors. */
    val firstShortDate: LocalDate? = null,
) {
    val shortage: Boolean get() = reasons.isNotEmpty()
}

data class ReadinessTeam(val id: UUID, val name: String)

data class ReadinessNurse(val id: UUID, val teamId: UUID?, val seniorEligible: Boolean, val workPattern: WorkPattern)

data class ReadinessSlot(
    val date: LocalDate,
    val kind: ShiftKind,
    val teamId: UUID?,
    val seniorRequired: Boolean,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class WardSetupInput(
    val activeShiftKinds: Set<ShiftKind>,
    val teams: List<ReadinessTeam>,
    val nurses: List<ReadinessNurse>,
    val maxConsecutiveWorkDays: Int,
    val maxOffRequestsPerMonth: Int,
    val offRequestDeadlineDay: Int,
    val targetHourPolicySet: Boolean,
)

data class RosterSetupInput(
    val month: YearMonth,
    val slots: List<ReadinessSlot>,
    val needsRevalidation: Boolean,
    val holidaysKnown: Boolean,
    val pendingOffRequests: Int,
)

object SetupReadinessEvaluator {
    private val rosterKinds = listOf(ShiftKind.DAY, ShiftKind.EVENING, ShiftKind.NIGHT)

    fun ward(input: WardSetupInput): List<ReadinessItem> = listOf(
        shiftTypes(input), staff(input), teamSeniors(input),
        ReadinessItem(ReadinessKey.WORK_RULES, ReadinessStatus.COMPLETE, "최대 연속 근무 ${input.maxConsecutiveWorkDays}일"),
        ReadinessItem(
            ReadinessKey.OFF_REQUEST_POLICY, ReadinessStatus.COMPLETE,
            "한도 ${input.maxOffRequestsPerMonth}일 · 전달 ${input.offRequestDeadlineDay}일 마감",
        ),
        if (input.targetHourPolicySet) {
            ReadinessItem(ReadinessKey.TARGET_HOURS, ReadinessStatus.COMPLETE, "목표 시간 기준이 설정되어 있습니다.")
        } else {
            ReadinessItem(ReadinessKey.TARGET_HOURS, ReadinessStatus.DEFAULT, "목표 시간 기준이 없어 기본값(하루 8시간)을 사용합니다.")
        },
    )

    fun roster(ward: WardSetupInput, roster: RosterSetupInput): List<ReadinessItem> = ward(ward) + listOf(
        requirements(roster),
        if (roster.holidaysKnown) {
            ReadinessItem(ReadinessKey.HOLIDAY_CALENDAR, ReadinessStatus.COMPLETE, "${roster.month} 휴일 달력이 있습니다.")
        } else {
            ReadinessItem(
                ReadinessKey.HOLIDAY_CALENDAR, ReadinessStatus.WARNING,
                "${roster.month} 공휴일을 아직 불러오지 않았습니다. 목표 시간이 주말만 빼고 계산됩니다.",
            )
        },
        if (roster.pendingOffRequests == 0) {
            ReadinessItem(ReadinessKey.PENDING_OFF_REQUESTS, ReadinessStatus.COMPLETE, "검토할 희망 오프가 없습니다.")
        } else {
            ReadinessItem(
                ReadinessKey.PENDING_OFF_REQUESTS, ReadinessStatus.WARNING,
                "확정하지 않은 희망 오프 ${roster.pendingOffRequests}건은 선호로만 반영됩니다.",
            )
        },
    )

    /**
     * Senior demand against supply per team and shift kind, plus the team as a whole. It follows the placement
     * rules: a nurse may hold several slots of one day if they do not overlap (half-open intervals), and the
     * consecutive-work limit counts working days, not slots. A lower bound only: a team with no shortage here can
     * still end with unfilled senior slots once leave, rest and night rules apply. Cross-team support is not
     * counted as supply, so the notice errs towards showing a shortage.
     */
    fun seniorCoverage(ward: WardSetupInput, roster: RosterSetupInput): List<SeniorCoverage> {
        val maxDays = maxShiftsPerNurse(roster.month, ward.maxConsecutiveWorkDays)
        val seniorSlots = roster.slots.filter { it.seniorRequired && it.teamId != null }
        val seniors = ward.nurses.filter { it.seniorEligible && it.teamId != null }
        return ward.teams.flatMap { team ->
            val teamSlots = seniorSlots.filter { it.teamId == team.id }
            if (teamSlots.isEmpty()) return@flatMap emptyList()
            val teamSeniors = seniors.filter { it.teamId == team.id }
            val perKind = rosterKinds.mapNotNull { kind ->
                val slots = teamSlots.filter { it.kind == kind }
                if (slots.isEmpty()) return@mapNotNull null
                coverage(team, kind, slots, teamSeniors.count { it.workPattern.allows(kind) }, maxDays)
            }
            perKind + coverage(team, null, teamSlots, teamSeniors.size, maxDays)
        }
    }

    private fun coverage(team: ReadinessTeam, kind: ShiftKind?, slots: List<ReadinessSlot>, available: Int, maxDays: Int): SeniorCoverage {
        val days = slots.groupBy { it.date }.values.sumOf { maxConcurrent(it) }
        val peak = maxConcurrent(slots)
        return SeniorCoverage(
            team.id, team.name, kind, slots.size, available, available * maxDays, days,
            buildList {
                if (available == 0) add(SeniorShortageReason.NO_SENIOR)
                else {
                    if (days > available * maxDays) add(SeniorShortageReason.MONTHLY_CAPACITY)
                    if (peak > available) add(SeniorShortageReason.SAME_DAY)
                }
            },
            if (available > 0 && peak > available) firstDateAbove(slots, available) else null,
        )
    }

    /** Most slots running at one instant: the fewest nurses that can hold them all. Ends sort before starts at equal times. */
    private fun maxConcurrent(slots: List<ReadinessSlot>): Int {
        var current = 0
        var peak = 0
        events(slots).forEach { (_, delta, _) ->
            current += delta
            peak = maxOf(peak, current)
        }
        return peak
    }

    private fun firstDateAbove(slots: List<ReadinessSlot>, limit: Int): LocalDate? {
        var current = 0
        events(slots).forEach { (_, delta, slot) ->
            current += delta
            if (current > limit) return slot.date
        }
        return null
    }

    private fun events(slots: List<ReadinessSlot>): List<Triple<Instant, Int, ReadinessSlot>> =
        slots.flatMap { listOf(Triple(it.startsAt, 1, it), Triple(it.endsAt, -1, it)) }
            .sortedWith(compareBy({ it.first }, { it.second }))

    /**
     * Most working days one nurse can have in the month with a day off after every [maxConsecutive] working
     * days. Rest days, leave and night rest make the real number smaller.
     */
    fun maxShiftsPerNurse(month: YearMonth, maxConsecutive: Int): Int {
        val days = month.lengthOfMonth()
        return days - days / (maxConsecutive + 1)
    }

    private fun shiftTypes(input: WardSetupInput): ReadinessItem {
        val present = rosterKinds.filter { it in input.activeShiftKinds }
        val absent = rosterKinds - present.toSet()
        return when {
            present.isEmpty() -> ReadinessItem(
                ReadinessKey.SHIFT_TYPES, ReadinessStatus.MISSING, "D/E/N 근무 유형이 없습니다.", absent.map { it.name },
            )
            absent.isNotEmpty() -> ReadinessItem(
                ReadinessKey.SHIFT_TYPES, ReadinessStatus.WARNING,
                "${absent.joinToString("·") { it.name.first().toString() }} 근무 유형이 없습니다.", absent.map { it.name },
            )
            else -> ReadinessItem(ReadinessKey.SHIFT_TYPES, ReadinessStatus.COMPLETE, "D·E·N 근무 유형이 있습니다.")
        }
    }

    private fun staff(input: WardSetupInput): ReadinessItem {
        if (input.nurses.isEmpty()) {
            return ReadinessItem(ReadinessKey.STAFF, ReadinessStatus.MISSING, "편성할 수 있는 간호사가 없습니다.")
        }
        val teamIds = input.teams.map { it.id }.toSet()
        val withoutTeam = if (teamIds.isEmpty()) emptyList() else input.nurses.filter { it.teamId !in teamIds }
        return if (withoutTeam.isEmpty()) {
            ReadinessItem(ReadinessKey.STAFF, ReadinessStatus.COMPLETE, "${input.nurses.size}명 (팀 ${input.teams.size}개)")
        } else {
            ReadinessItem(
                ReadinessKey.STAFF, ReadinessStatus.WARNING,
                "팀이 정해지지 않은 간호사 ${withoutTeam.size}명은 팀 근무 칸에 배치되지 않습니다.",
                withoutTeam.map { it.id.toString() },
            )
        }
    }

    private fun teamSeniors(input: WardSetupInput): ReadinessItem {
        val noSenior = input.teams.filter { team -> input.nurses.none { it.teamId == team.id && it.seniorEligible } }
        return when {
            input.teams.isEmpty() -> ReadinessItem(
                ReadinessKey.TEAM_SENIORS, ReadinessStatus.COMPLETE, "팀이 없어 선임 필수 칸이 생기지 않습니다.",
            )
            noSenior.isEmpty() -> ReadinessItem(ReadinessKey.TEAM_SENIORS, ReadinessStatus.COMPLETE, "모든 팀에 선임이 있습니다.")
            else -> ReadinessItem(
                ReadinessKey.TEAM_SENIORS, ReadinessStatus.WARNING,
                "${noSenior.joinToString(", ") { it.name }}에 선임 가능한 간호사가 없어 선임 칸이 비게 됩니다.",
                noSenior.map { it.id.toString() },
            )
        }
    }

    private fun requirements(roster: RosterSetupInput): ReadinessItem = when {
        roster.slots.isEmpty() -> ReadinessItem(
            ReadinessKey.REQUIREMENTS, ReadinessStatus.MISSING, "${roster.month} 필요 인원이 입력되지 않았습니다.",
        )
        roster.needsRevalidation -> ReadinessItem(
            ReadinessKey.REQUIREMENTS, ReadinessStatus.WARNING, "설정이 바뀌어 초안을 다시 검사해야 합니다.",
        )
        else -> ReadinessItem(
            ReadinessKey.REQUIREMENTS, ReadinessStatus.COMPLETE,
            "${roster.slots.map { it.date }.distinct().size}일 · ${roster.slots.size}칸",
        )
    }
}
