package com.nurseflow.policy.domain

import com.nurseflow.roster.domain.model.ApprovedAbsenceKind
import com.nurseflow.roster.domain.model.RestDayKind
import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.domain.model.WorkPattern
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/**
 * One date on a holiday calendar. Holidays and ward rest days add rest days on top of the weekend;
 * WARD_WORKDAY turns a weekend or imported holiday into a target day. One entry per date.
 */
enum class CalendarDayKind { PUBLIC_HOLIDAY, SUBSTITUTE_HOLIDAY, WARD_REST_DAY, WARD_WORKDAY }

data class CalendarDay(val date: LocalDate, val kind: CalendarDayKind, val name: String?)

/**
 * Ward target-hour settings in minutes. [dailyStandardMinutes] times the month's target days is the general
 * monthly target. Leave, half-day and SOF credits count toward a nurse's credited time. Null Night-dedicated
 * values mean "same as general": the general monthly target, and each shift type's own credit.
 * These are ward settings with demo defaults, never legal limits.
 */
data class TargetHourPolicy(
    val dailyStandardMinutes: Int = DEFAULT_DAILY_MINUTES,
    val leaveCreditMinutes: Int = DEFAULT_DAILY_MINUTES,
    val halfDayCreditMinutes: Int = DEFAULT_DAILY_MINUTES / 2,
    val sofCreditMinutes: Int = DEFAULT_DAILY_MINUTES,
    val nightDedicatedTargetMinutes: Int? = null,
    val nightDedicatedNightCreditMinutes: Int? = null,
) {
    companion object {
        const val DEFAULT_DAILY_MINUTES = 480
    }
}

/** The month's rest days are weekends and holidays counted once (decision 14), minus ward workdays. */
data class MonthTarget(
    val month: YearMonth,
    val daysInMonth: Int,
    val weekendDays: Int,
    val holidayDays: Int,
    val wardRestDays: Int,
    val wardWorkdays: Int,
    val restDays: Int,
    val targetDays: Int,
    val dailyStandardMinutes: Int,
    val generalTargetMinutes: Int,
    /** Rest dates of the month: leave and half-days on them are not credited, since the target already omits them. */
    val restDates: Set<LocalDate> = emptySet(),
)

/** A worked shift with the minutes of its type: [recognizedMinutes] is actual time, [creditMinutes] counts to the target. */
data class HourShift(
    val nurseId: UUID,
    val shiftDate: LocalDate,
    val kind: ShiftKind,
    val startsAt: Instant,
    val endsAt: Instant,
    val recognizedMinutes: Int,
    val creditMinutes: Int,
)

data class HourAbsence(val nurseId: UUID, val kind: ApprovedAbsenceKind, val startsAt: Instant, val endsAt: Instant)

data class HourRestDay(val nurseId: UUID, val date: LocalDate, val kind: RestDayKind)

data class HourNurse(val nurseId: UUID, val displayName: String, val workPattern: WorkPattern, val rosterable: Boolean)

enum class TargetStatus { SHORTFALL, MET, OVER }

data class NurseHours(
    val nurse: HourNurse,
    val shiftCount: Int,
    val actualMinutes: Int,
    val shiftCreditMinutes: Int,
    val leaveDays: Int,
    val halfDays: Int,
    val sofDays: Int,
    val absenceCreditMinutes: Int,
    val creditMinutes: Int,
    val targetMinutes: Int,
    /** credit - target: negative is a shortfall, positive is time over the target. */
    val remainingMinutes: Int,
    val status: TargetStatus,
    /** Shifts of the previous month's last day that end in this month and so count here (decision 14). */
    val carriedInShifts: Int,
    /** Shifts of this month's last day that end next month and so count there, not here. */
    val carriedOutShifts: Int,
)

object TargetHourCalculator {
    private val weekend = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

    /**
     * [wardDays] are the ward's own entries and win over [publicHolidays] (imported national holidays) on the same
     * date, so a ward can drop an imported holiday with WARD_WORKDAY or add one the source lacks.
     */
    fun monthTarget(
        month: YearMonth,
        wardDays: List<CalendarDay>,
        dailyStandardMinutes: Int,
        publicHolidays: List<CalendarDay> = emptyList(),
    ): MonthTarget {
        val imported = publicHolidays.filter { YearMonth.from(it.date) == month }.associateBy { it.date }
        val effective = imported + wardDays.filter { YearMonth.from(it.date) == month }.associateBy { it.date }
        val dates = (1..month.lengthOfMonth()).map { month.atDay(it) }
        val weekendDates = dates.filter { it.dayOfWeek in weekend }.toSet()
        fun kind(date: LocalDate) = effective[date]?.kind
        // A workday counts only where it turns a weekend or an imported holiday into a target day.
        val workdays = dates.filter { kind(it) == CalendarDayKind.WARD_WORKDAY && (it in weekendDates || it in imported) }.toSet()
        // Holidays on a weekend are already rest days: counting them again is what decision 14 rules out.
        val holidays = dates.filter {
            it !in weekendDates && (kind(it) == CalendarDayKind.PUBLIC_HOLIDAY || kind(it) == CalendarDayKind.SUBSTITUTE_HOLIDAY)
        }.toSet()
        val wardRest = dates.filter { it !in weekendDates && kind(it) == CalendarDayKind.WARD_REST_DAY }.toSet()
        val restDates = (weekendDates - workdays) + holidays + wardRest
        val restDays = restDates.size
        val targetDays = dates.size - restDays
        return MonthTarget(
            month, dates.size, weekendDates.size, holidays.size, wardRest.size, workdays.size, restDays, targetDays,
            dailyStandardMinutes, targetDays * dailyStandardMinutes, restDates,
        )
    }

    /**
     * The month a shift's hours count toward. A shift that crosses midnight into the next month (the Night of
     * the month's last day) counts entirely toward the next month (decision 14, provisional).
     */
    fun attributedMonth(shift: HourShift, zone: ZoneId): YearMonth {
        val lastInstant = shift.endsAt.minusNanos(1)
        val endDate = lastInstant.atZone(zone).toLocalDate()
        return YearMonth.from(if (endDate.isAfter(shift.shiftDate)) endDate else shift.shiftDate)
    }

    fun diagnose(
        month: YearMonth,
        zone: ZoneId,
        target: MonthTarget,
        policy: TargetHourPolicy,
        nurses: List<HourNurse>,
        shifts: List<HourShift>,
        absences: List<HourAbsence>,
        restDays: List<HourRestDay>,
    ): List<NurseHours> = nurses.map { nurse ->
        val dedicated = nurse.workPattern == WorkPattern.NIGHT_ONLY
        val own = shifts.filter { it.nurseId == nurse.nurseId }
        val counted = own.filter { attributedMonth(it, zone) == month }
        val carriedIn = counted.count { YearMonth.from(it.shiftDate) != month }
        val carriedOut = own.count { YearMonth.from(it.shiftDate) == month && attributedMonth(it, zone) != month }
        val shiftCredit = counted.sumOf {
            if (dedicated && it.kind == ShiftKind.NIGHT) policy.nightDedicatedNightCreditMinutes ?: it.creditMinutes else it.creditMinutes
        }
        val sofDates = restDays.filter { it.nurseId == nurse.nurseId && it.kind == RestDayKind.SOF && YearMonth.from(it.date) == month }
            .map { it.date }.toSet()
        // Leave on a weekend, holiday or ward rest day credits nothing: the target already leaves that date out.
        val leaveDates = absences.filter { it.nurseId == nurse.nurseId && it.kind == ApprovedAbsenceKind.APPROVED_LEAVE }
            .flatMap { leaveDatesInMonth(it, month, zone) }.filter { it !in target.restDates }.toSet()
        // A half-day on a date already credited as leave or SOF, or on a rest date, adds nothing.
        val halfDays = absences.filter { it.nurseId == nurse.nurseId && it.kind == ApprovedAbsenceKind.APPROVED_HALF_DAY }
            .map { it.startsAt.atZone(zone).toLocalDate() }
            .filter { YearMonth.from(it) == month && it !in leaveDates && it !in sofDates && it !in target.restDates }
            .toSet().size
        val sofDays = sofDates.size
        val absenceCredit = leaveDates.size * policy.leaveCreditMinutes + halfDays * policy.halfDayCreditMinutes +
            sofDays * policy.sofCreditMinutes
        val credit = shiftCredit + absenceCredit
        val targetMinutes = if (dedicated) policy.nightDedicatedTargetMinutes ?: target.generalTargetMinutes else target.generalTargetMinutes
        val remaining = credit - targetMinutes
        NurseHours(
            nurse, counted.size, counted.sumOf { it.recognizedMinutes }, shiftCredit, leaveDates.size, halfDays, sofDays,
            absenceCredit, credit, targetMinutes, remaining,
            when { remaining < 0 -> TargetStatus.SHORTFALL; remaining > 0 -> TargetStatus.OVER; else -> TargetStatus.MET },
            carriedIn, carriedOut,
        )
    }

    /** Ward-local dates of [month] that an approved leave interval overlaps. */
    private fun leaveDatesInMonth(absence: HourAbsence, month: YearMonth, zone: ZoneId): List<LocalDate> =
        (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { date ->
            val start = date.atStartOfDay(zone).toInstant()
            val end = date.plusDays(1).atStartOfDay(zone).toInstant()
            absence.startsAt < end && absence.endsAt > start
        }
}
