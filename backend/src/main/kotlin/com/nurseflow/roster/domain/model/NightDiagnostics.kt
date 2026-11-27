package com.nurseflow.roster.domain.model

import com.nurseflow.policy.domain.NightPolicy
import java.time.YearMonth
import java.util.UUID

/** SOF state of one nurse in one month. Night-dedicated nurses follow the ward's dedicated rest policy instead. */
enum class SofStatus { NOT_REQUIRED, MISSING, MET, EXCESS, DEDICATED_EXEMPT }

enum class DedicatedNightStatus { BELOW_MINIMUM, MET }

enum class DedicatedHeadcountStatus { SHORTAGE, MET, EXCESS }

/** Something the admin should look at; none of these block saving or confirming (A15 diagnoses only). */
enum class NightIssue {
    /** Recorded as dedicated this month, but the work pattern still allows Day/Evening. */
    WORK_PATTERN_NOT_NIGHT_ONLY,

    /** NIGHT_ONLY work pattern without a dedicated term covering this month. */
    NO_DEDICATED_TERM,

    /** A dedicated term covers this month, but the nurse is inactive or no longer rosterable. */
    NOT_ROSTERABLE,

    /** Back-to-back terms make the nurse dedicated for longer than the ward's rotation. */
    ROTATION_EXCEEDED,
}

data class NightNurse(val nurseId: UUID, val displayName: String, val workPattern: WorkPattern, val rosterable: Boolean)

data class NurseNightDiagnosis(
    val nurse: NightNurse,
    val nightCount: Int,
    val sofDays: Int,
    val dedicated: Boolean,
    val dedicatedTermId: UUID?,
    /** Months in a row the nurse is covered by dedicated terms, including this one; 0 without a term. */
    val dedicatedRunMonths: Int,
    val sofRequiredDays: Int,
    val sofStatus: SofStatus,
    val dedicatedNightStatus: DedicatedNightStatus?,
    val nightShortfall: Int,
    val issues: List<NightIssue>,
)

data class DedicatedHeadcount(
    val required: Int,
    val assigned: Int,
    val status: DedicatedHeadcountStatus,
    val nurseIds: List<UUID>,
)

data class NightMonthDiagnosis(val headcount: DedicatedHeadcount, val nurses: List<NurseNightDiagnosis>)

/**
 * SOF need and Night-dedicated checks for one month (A15). Nights are counted by the date the shift starts, the
 * same date the roster shows it on, so the last-day Night of a month counts in that month.
 */
object NightDiagnosticsCalculator {
    fun diagnose(
        month: YearMonth,
        policy: NightPolicy,
        nurses: List<NightNurse>,
        nightCounts: Map<UUID, Int>,
        sofCounts: Map<UUID, Int>,
        terms: List<NightDedicatedTerm>,
    ): NightMonthDiagnosis {
        val live = terms.filter { it.status != NightDedicatedTermStatus.CANCELLED }
        val byNurse = live.groupBy { it.nurseId }
        val items = nurses.map { nurse ->
            val own = byNurse[nurse.nurseId].orEmpty()
            val term = own.firstOrNull { it.covers(month) }
            val nights = nightCounts[nurse.nurseId] ?: 0
            val sof = sofCounts[nurse.nurseId] ?: 0
            val dedicated = term != null || nurse.workPattern == WorkPattern.NIGHT_ONLY
            val required = if (!dedicated && policy.sofDays > 0 && nights >= policy.sofNightThreshold) policy.sofDays else 0
            val sofStatus = when {
                dedicated -> SofStatus.DEDICATED_EXEMPT
                sof < required -> SofStatus.MISSING
                required == 0 && sof == 0 -> SofStatus.NOT_REQUIRED
                sof == required -> SofStatus.MET
                else -> SofStatus.EXCESS
            }
            val run = if (term == null) 0 else runLength(month, own)
            val issues = buildList {
                if (term != null && nurse.workPattern != WorkPattern.NIGHT_ONLY) add(NightIssue.WORK_PATTERN_NOT_NIGHT_ONLY)
                if (term == null && nurse.workPattern == WorkPattern.NIGHT_ONLY) add(NightIssue.NO_DEDICATED_TERM)
                if (term != null && !nurse.rosterable) add(NightIssue.NOT_ROSTERABLE)
                if (run > policy.dedicatedRotationMonths) add(NightIssue.ROTATION_EXCEEDED)
            }
            NurseNightDiagnosis(
                nurse, nights, sof, dedicated, term?.id, run, required, sofStatus,
                if (dedicated) if (nights >= policy.dedicatedMinNights) DedicatedNightStatus.MET else DedicatedNightStatus.BELOW_MINIMUM else null,
                if (dedicated) maxOf(0, policy.dedicatedMinNights - nights) else 0,
                issues,
            )
        }
        // Only a recorded term that a rosterable nurse can serve fills the rotation; a bare NIGHT_ONLY pattern does not.
        val serving = items.filter { it.dedicatedTermId != null && it.nurse.rosterable }.map { it.nurse.nurseId }
        val status = when {
            serving.size < policy.dedicatedHeadcount -> DedicatedHeadcountStatus.SHORTAGE
            serving.size > policy.dedicatedHeadcount -> DedicatedHeadcountStatus.EXCESS
            else -> DedicatedHeadcountStatus.MET
        }
        return NightMonthDiagnosis(DedicatedHeadcount(policy.dedicatedHeadcount, serving.size, status, serving), items)
    }

    /** Length of the unbroken run of covered months that contains [month]. */
    private fun runLength(month: YearMonth, terms: List<NightDedicatedTerm>): Int {
        fun covered(m: YearMonth) = terms.any { it.covers(m) }
        var start = month
        while (covered(start.minusMonths(1))) start = start.minusMonths(1)
        var end = month
        while (covered(end.plusMonths(1))) end = end.plusMonths(1)
        return (end.year - start.year) * 12 + end.monthValue - start.monthValue + 1
    }
}
