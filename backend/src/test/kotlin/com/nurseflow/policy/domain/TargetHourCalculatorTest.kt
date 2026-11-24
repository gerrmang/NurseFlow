package com.nurseflow.policy.domain

import com.nurseflow.roster.domain.model.ApprovedAbsenceKind
import com.nurseflow.roster.domain.model.RestDayKind
import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.domain.model.WorkPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class TargetHourCalculatorTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val november = YearMonth.of(2026, 11)

    @Test
    fun `30 days with 9 weekend rest days at 8 hours is 168 hours`() {
        val target = TargetHourCalculator.monthTarget(november, emptyList(), 480)
        assertEquals(30, target.daysInMonth)
        assertEquals(9, target.weekendDays)
        assertEquals(9, target.restDays)
        assertEquals(21, target.targetDays)
        assertEquals(168 * 60, target.generalTargetMinutes)
    }

    @Test
    fun `a holiday on a weekend counts once and ward exceptions move the count`() {
        val calendar = listOf(
            CalendarDay(LocalDate.of(2026, 11, 7), CalendarDayKind.PUBLIC_HOLIDAY, "가상 공휴일"), // Saturday
            CalendarDay(LocalDate.of(2026, 11, 9), CalendarDayKind.SUBSTITUTE_HOLIDAY, null), // Monday
            CalendarDay(LocalDate.of(2026, 11, 10), CalendarDayKind.WARD_REST_DAY, null),
            CalendarDay(LocalDate.of(2026, 11, 29), CalendarDayKind.WARD_WORKDAY, null), // Sunday
            CalendarDay(LocalDate.of(2026, 12, 1), CalendarDayKind.PUBLIC_HOLIDAY, null), // other month: ignored
        )
        val target = TargetHourCalculator.monthTarget(november, calendar, 480)
        assertEquals(1, target.holidayDays)
        assertEquals(1, target.wardRestDays)
        assertEquals(1, target.wardWorkdays)
        assertEquals(9 + 1 + 1 - 1, target.restDays)
        assertEquals(20, target.targetDays)
        assertEquals(20 * 480, target.generalTargetMinutes)
    }

    @Test
    fun `a ward entry replaces an imported holiday on the same date`() {
        val imported = listOf(
            CalendarDay(LocalDate.of(2026, 11, 9), CalendarDayKind.PUBLIC_HOLIDAY, null), // Monday
            CalendarDay(LocalDate.of(2026, 11, 11), CalendarDayKind.SUBSTITUTE_HOLIDAY, null), // Wednesday
        )
        val ward = listOf(CalendarDay(LocalDate.of(2026, 11, 11), CalendarDayKind.WARD_WORKDAY, null))
        val target = TargetHourCalculator.monthTarget(november, ward, 480, imported)
        assertEquals(1, target.holidayDays)
        assertEquals(1, target.wardWorkdays)
        assertEquals(10, target.restDays)
        // A workday on a plain weekday changes nothing and is not counted.
        val plain = TargetHourCalculator.monthTarget(november, listOf(CalendarDay(LocalDate.of(2026, 11, 12), CalendarDayKind.WARD_WORKDAY, null)), 480)
        assertEquals(0, plain.wardWorkdays)
        assertEquals(9, plain.restDays)
    }

    @Test
    fun `a night crossing into the next month counts there, a night ending on the same date stays`() {
        val nurse = UUID.randomUUID()
        assertEquals(YearMonth.of(2026, 12), TargetHourCalculator.attributedMonth(night(nurse, LocalDate.of(2026, 11, 30)), zone))
        assertEquals(november, TargetHourCalculator.attributedMonth(night(nurse, LocalDate.of(2026, 10, 31)), zone))
        assertEquals(november, TargetHourCalculator.attributedMonth(night(nurse, LocalDate.of(2026, 11, 15)), zone))
        // A shift ending exactly at midnight does not cross into the next date.
        val evening = shift(nurse, LocalDate.of(2026, 11, 30), ShiftKind.EVENING, LocalTime.of(16, 0), 8 * 60)
        assertEquals(november, TargetHourCalculator.attributedMonth(evening, zone))
    }

    @Test
    fun `diagnosis separates actual and credited time and splits shortfall and overtime`() {
        val general = HourNurse(UUID.randomUUID(), "가상 간호사 1", WorkPattern.GENERAL, true)
        val dedicated = HourNurse(UUID.randomUUID(), "가상 간호사 2", WorkPattern.NIGHT_ONLY, true)
        val short = HourNurse(UUID.randomUUID(), "가상 간호사 3", WorkPattern.GENERAL, true)
        val weekdays = (1..30).map { november.atDay(it) }.filter { it.dayOfWeek.value <= 5 }
        val shifts = weekdays.map { shift(general.nurseId, it, ShiftKind.DAY, LocalTime.of(7, 0), 8 * 60, recognized = 450, credit = 480) } +
            // Oct 31 night carries in; Nov 30 night carries out.
            listOf(night(dedicated.nurseId, LocalDate.of(2026, 10, 31)), night(dedicated.nurseId, LocalDate.of(2026, 11, 30))) +
            (1..14).map { night(dedicated.nurseId, november.atDay(it)) } +
            (1..2).map { shift(short.nurseId, november.atDay(it + 1), ShiftKind.DAY, LocalTime.of(7, 0), 8 * 60) }
        val leaveStart = LocalDate.of(2026, 11, 16).atStartOfDay(zone).toInstant()
        val absences = listOf(
            HourAbsence(short.nurseId, ApprovedAbsenceKind.APPROVED_LEAVE, leaveStart, leaveStart.plusSeconds(2 * 86_400)),
            // Inside the leave: no extra credit.
            HourAbsence(short.nurseId, ApprovedAbsenceKind.APPROVED_HALF_DAY, leaveStart.plusSeconds(3600), leaveStart.plusSeconds(4 * 3600)),
            HourAbsence(short.nurseId, ApprovedAbsenceKind.APPROVED_HALF_DAY, leaveStart.plusSeconds(3 * 86_400), leaveStart.plusSeconds(3 * 86_400 + 4 * 3600)),
        )
        val rests = listOf(HourRestDay(short.nurseId, november.atDay(20), RestDayKind.SOF), HourRestDay(short.nurseId, november.atDay(21), RestDayKind.OF))
        val policy = TargetHourPolicy(nightDedicatedTargetMinutes = 15 * 600, nightDedicatedNightCreditMinutes = 600)
        val target = TargetHourCalculator.monthTarget(november, emptyList(), policy.dailyStandardMinutes)
        val result = TargetHourCalculator.diagnose(november, zone, target, policy, listOf(general, dedicated, short), shifts, absences, rests)
            .associateBy { it.nurse.nurseId }

        val g = result.getValue(general.nurseId)
        assertEquals(21, g.shiftCount)
        assertEquals(21 * 450, g.actualMinutes)
        assertEquals(21 * 480, g.creditMinutes)
        assertEquals(0, g.remainingMinutes)
        assertEquals(TargetStatus.MET, g.status)

        val d = result.getValue(dedicated.nurseId)
        assertEquals(15, d.shiftCount)
        assertEquals(1, d.carriedInShifts)
        assertEquals(1, d.carriedOutShifts)
        assertEquals(15 * 600, d.shiftCreditMinutes)
        assertEquals(15 * 600, d.targetMinutes)
        assertEquals(TargetStatus.MET, d.status)

        val s = result.getValue(short.nurseId)
        assertEquals(2, s.leaveDays)
        assertEquals(1, s.halfDays)
        assertEquals(1, s.sofDays)
        assertEquals(2 * 480 + 240 + 480, s.absenceCreditMinutes)
        assertEquals(2 * 480 + 2 * 480 + 240 + 480, s.creditMinutes)
        assertEquals(s.creditMinutes - 168 * 60, s.remainingMinutes)
        assertEquals(TargetStatus.SHORTFALL, s.status)
    }

    @Test
    fun `time off on rest dates or on an already credited date adds no credit`() {
        val nurse = HourNurse(UUID.randomUUID(), "가상 간호사", WorkPattern.GENERAL, true)
        val friday = LocalDate.of(2026, 11, 13).atStartOfDay(zone).toInstant()
        val calendar = listOf(CalendarDay(LocalDate.of(2026, 11, 16), CalendarDayKind.PUBLIC_HOLIDAY, null))
        val target = TargetHourCalculator.monthTarget(november, calendar, 480)
        // Friday to Tuesday: Saturday, Sunday and the Monday holiday are already outside the target.
        val absences = listOf(
            HourAbsence(nurse.nurseId, ApprovedAbsenceKind.APPROVED_LEAVE, friday, friday.plusSeconds(5 * 86_400)),
            HourAbsence(nurse.nurseId, ApprovedAbsenceKind.APPROVED_HALF_DAY, friday.plusSeconds(13 * 86_400), friday.plusSeconds(13 * 86_400 + 14_400)),
            HourAbsence(nurse.nurseId, ApprovedAbsenceKind.APPROVED_HALF_DAY, friday.plusSeconds(14 * 86_400), friday.plusSeconds(14 * 86_400 + 14_400)),
        )
        val rests = listOf(
            HourRestDay(nurse.nurseId, LocalDate.of(2026, 11, 26), RestDayKind.SOF),
            HourRestDay(nurse.nurseId, LocalDate.of(2026, 11, 28), RestDayKind.SOF), // Saturday: outside the target
            HourRestDay(nurse.nurseId, LocalDate.of(2026, 11, 13), RestDayKind.SOF), // already credited as leave
        )
        val result = TargetHourCalculator.diagnose(november, zone, target, TargetHourPolicy(), listOf(nurse), emptyList(), absences, rests).single()
        assertEquals(2, result.leaveDays)
        assertEquals(1, result.halfDays)
        assertEquals(1, result.sofDays)
        assertEquals(2 * 480 + 240 + 480, result.absenceCreditMinutes)
    }

    private fun night(nurse: UUID, date: LocalDate) = shift(nurse, date, ShiftKind.NIGHT, LocalTime.of(23, 0), 8 * 60)

    private fun shift(
        nurse: UUID, date: LocalDate, kind: ShiftKind, start: LocalTime, minutes: Long,
        recognized: Int = minutes.toInt(), credit: Int = recognized,
    ): HourShift {
        val startsAt = date.atTime(start).atZone(zone).toInstant()
        return HourShift(nurse, date, kind, startsAt, startsAt.plusSeconds(minutes * 60), recognized, credit)
    }
}
