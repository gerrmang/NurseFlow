package com.nurseflow.roster.domain.readiness

import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.domain.model.WorkPattern
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.YearMonth
import java.util.UUID

class SetupReadinessEvaluatorTest {
    private val teamA = ReadinessTeam(UUID.randomUUID(), "A팀")
    private val teamB = ReadinessTeam(UUID.randomUUID(), "B팀")
    private val month = YearMonth.of(2027, 1)

    private fun ward(
        kinds: Set<ShiftKind> = setOf(ShiftKind.DAY, ShiftKind.EVENING, ShiftKind.NIGHT),
        teams: List<ReadinessTeam> = listOf(teamA, teamB),
        nurses: List<ReadinessNurse> = listOf(nurse(teamA, senior = true), nurse(teamB, senior = true)),
        targetSet: Boolean = true,
    ) = WardSetupInput(kinds, teams, nurses, 5, 4, 15, targetSet)

    private fun nurse(team: ReadinessTeam?, senior: Boolean = false, pattern: WorkPattern = WorkPattern.GENERAL) =
        ReadinessNurse(UUID.randomUUID(), team?.id, senior, pattern)

    private fun roster(slots: List<ReadinessSlot> = listOf(seniorSlot(teamA, 1, ShiftKind.DAY)), holidays: Boolean = true, pending: Int = 0) =
        RosterSetupInput(month, slots, false, holidays, pending)

    private val zone = ZoneId.of("Asia/Seoul")

    /** Adjacent, non-overlapping D 07–15, E 15–23, N 23–07. */
    private fun seniorSlot(team: ReadinessTeam, day: Int, kind: ShiftKind) = when (kind) {
        ShiftKind.DAY -> slot(team, day, kind, "07:00", "15:00")
        ShiftKind.EVENING -> slot(team, day, kind, "15:00", "23:00")
        else -> slot(team, day, kind, "23:00", "07:00")
    }

    private fun slot(team: ReadinessTeam, day: Int, kind: ShiftKind, start: String, end: String): ReadinessSlot {
        val date = month.atDay(day)
        val s = LocalTime.parse(start)
        val e = LocalTime.parse(end)
        val endDate = if (e <= s) date.plusDays(1) else date
        return ReadinessSlot(date, kind, team.id, true, date.atTime(s).atZone(zone).toInstant(), endDate.atTime(e).atZone(zone).toInstant())
    }

    private fun List<ReadinessItem>.of(key: ReadinessKey) = single { it.key == key }

    @Test
    fun `complete ward has no blocking items and defaults are reported`() {
        val items = SetupReadinessEvaluator.ward(ward(targetSet = false))
        assertTrue(items.none { it.blocking })
        assertEquals(ReadinessStatus.DEFAULT, items.of(ReadinessKey.TARGET_HOURS).status)
        assertEquals(ReadinessStatus.COMPLETE, items.of(ReadinessKey.SHIFT_TYPES).status)
        assertEquals(ReadinessStatus.COMPLETE, items.of(ReadinessKey.TEAM_SENIORS).status)
    }

    @Test
    fun `each missing required item is reported separately and blocks`() {
        val empty = SetupReadinessEvaluator.roster(ward(kinds = emptySet(), teams = emptyList(), nurses = emptyList()), roster(slots = emptyList()))
        assertEquals(
            listOf(ReadinessKey.SHIFT_TYPES, ReadinessKey.STAFF, ReadinessKey.REQUIREMENTS),
            empty.filter { it.blocking }.map { it.key },
        )
        assertEquals(listOf("DAY", "EVENING", "NIGHT"), empty.of(ReadinessKey.SHIFT_TYPES).missing)
    }

    @Test
    fun `partial shift kinds unassigned nurses and seniorless teams warn without blocking`() {
        val items = SetupReadinessEvaluator.roster(
            ward(kinds = setOf(ShiftKind.DAY, ShiftKind.NIGHT), nurses = listOf(nurse(teamA, senior = true), nurse(null))),
            roster(holidays = false, pending = 3),
        )
        assertTrue(items.none { it.blocking })
        assertEquals(listOf("EVENING"), items.of(ReadinessKey.SHIFT_TYPES).missing)
        assertEquals(ReadinessStatus.WARNING, items.of(ReadinessKey.STAFF).status)
        assertEquals(listOf(teamB.id.toString()), items.of(ReadinessKey.TEAM_SENIORS).missing)
        assertEquals(ReadinessStatus.WARNING, items.of(ReadinessKey.HOLIDAY_CALENDAR).status)
        assertTrue(items.of(ReadinessKey.PENDING_OFF_REQUESTS).message.contains("3건"))
    }

    @Test
    fun `max shifts per nurse follows the consecutive limit`() {
        assertEquals(26, SetupReadinessEvaluator.maxShiftsPerNurse(YearMonth.of(2027, 1), 5)) // 31 - 5
        assertEquals(25, SetupReadinessEvaluator.maxShiftsPerNurse(YearMonth.of(2026, 11), 5)) // 30 - 5
        assertEquals(14, SetupReadinessEvaluator.maxShiftsPerNurse(YearMonth.of(2026, 2), 1)) // 28 - 14
    }

    @Test
    fun `night senior slots for a whole month with one senior are a capacity shortage`() {
        val slots = (1..31).map { seniorSlot(teamB, it, ShiftKind.NIGHT) }
        val coverage = SetupReadinessEvaluator.seniorCoverage(
            ward(nurses = listOf(nurse(teamA, senior = true), nurse(teamB, senior = true))), roster(slots),
        )
        val night = coverage.single { it.teamId == teamB.id && it.shiftKind == ShiftKind.NIGHT }
        assertEquals(31, night.requiredSeniorSlots)
        assertEquals(1, night.availableSeniors)
        assertEquals(26, night.seniorShiftCapacity)
        assertEquals(listOf(SeniorShortageReason.MONTHLY_CAPACITY), night.reasons)
        assertTrue(coverage.none { it.teamId == teamA.id }, "teams without senior slots are not listed")
    }

    @Test
    fun `seniors whose work pattern cannot take the kind do not count`() {
        val coverage = SetupReadinessEvaluator.seniorCoverage(
            ward(nurses = listOf(nurse(teamA, senior = true, pattern = WorkPattern.DAY_EVENING_ONLY), nurse(teamA))),
            roster(listOf(seniorSlot(teamA, 1, ShiftKind.NIGHT), seniorSlot(teamA, 2, ShiftKind.DAY))),
        )
        val night = coverage.single { it.shiftKind == ShiftKind.NIGHT }
        assertEquals(0, night.availableSeniors)
        assertEquals(listOf(SeniorShortageReason.NO_SENIOR), night.reasons)
        assertFalse(coverage.single { it.shiftKind == ShiftKind.DAY }.shortage)
        val total = coverage.single { it.shiftKind == null }
        assertEquals(1, total.availableSeniors)
        assertFalse(total.shortage)
    }

    @Test
    fun `one senior can hold non-overlapping day and evening slots of one date`() {
        val slots = listOf(seniorSlot(teamA, 3, ShiftKind.DAY), seniorSlot(teamA, 3, ShiftKind.EVENING))
        val coverage = SetupReadinessEvaluator.seniorCoverage(ward(nurses = listOf(nurse(teamA, senior = true))), roster(slots))
        assertTrue(coverage.none { it.shortage }, coverage.toString())
        val total = coverage.single { it.shiftKind == null }
        assertEquals(2, total.requiredSeniorSlots)
        assertEquals(1, total.requiredSeniorDays)
    }

    @Test
    fun `overlapping senior slots need more seniors than the team has`() {
        // D 07:00–15:30 and E 15:00–23:30 overlap for half an hour, so one senior cannot hold both.
        val slots = listOf(slot(teamA, 3, ShiftKind.DAY, "07:00", "15:30"), slot(teamA, 3, ShiftKind.EVENING, "15:00", "23:30")) +
            seniorSlot(teamA, 4, ShiftKind.DAY)
        val coverage = SetupReadinessEvaluator.seniorCoverage(ward(nurses = listOf(nurse(teamA, senior = true))), roster(slots))
        val total = coverage.single { it.shiftKind == null }
        assertEquals(listOf(SeniorShortageReason.SAME_DAY), total.reasons)
        assertEquals(LocalDate.of(2027, 1, 3), total.firstShortDate)
        assertEquals(3, total.requiredSeniorDays) // 2 on the 3rd + 1 on the 4th
        assertTrue(coverage.filter { it.shiftKind != null }.none { it.shortage })
        assertNull(coverage.first { it.shiftKind != null }.firstShortDate)
    }

    @Test
    fun `a night running into the next day overlaps that day's early slot`() {
        val slots = listOf(slot(teamA, 3, ShiftKind.NIGHT, "22:00", "07:30"), slot(teamA, 4, ShiftKind.DAY, "07:00", "15:30"))
        val coverage = SetupReadinessEvaluator.seniorCoverage(ward(nurses = listOf(nurse(teamA, senior = true))), roster(slots))
        assertEquals(listOf(SeniorShortageReason.SAME_DAY), coverage.single { it.shiftKind == null }.reasons)
    }
}
