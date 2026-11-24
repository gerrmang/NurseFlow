package com.nurseflow.roster

import com.nurseflow.roster.domain.model.RestDayKind
import com.nurseflow.roster.domain.model.RosterRestDay
import com.nurseflow.roster.domain.model.ShiftAssignment
import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.domain.rule.ConsecutiveWorkRule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

class ConsecutiveWorkRuleTest {
    private val nurse = UUID.randomUUID()
    private val window = LocalDate.parse("2026-10-01")..LocalDate.parse("2026-12-31")

    @Test
    fun `work dates count once and only explicit rest breaks a block`() {
        val assignments = (26..29).map { shift("2026-10-$it") } + shift("2026-10-29")
        val rule = ConsecutiveWorkRule(5, window, emptyList())
        val fifth = shift("2026-10-31")
        val sixth = shift("2026-11-01")
        assertNull(rule.validate(fifth, assignments))
        assertEquals("CONSECUTIVE_WORK_LIMIT", rule.validate(sixth, assignments + fifth)?.code)

        val rest = RosterRestDay(nurse, LocalDate.parse("2026-10-30"), RestDayKind.SOF)
        val withRest = ConsecutiveWorkRule(5, window, listOf(rest))
        assertNull(withRest.validate(sixth, assignments + fifth))
        assertEquals("REST_DAY_ASSIGNMENT_CONFLICT", withRest.validate(shift("2026-10-30"), assignments)?.code)
    }

    @Test
    fun `new month may join formerly disconnected published runs`() {
        val month = LocalDate.parse("2026-11-01")..LocalDate.parse("2026-11-30")
        val previous = (28..31).map { shift("2026-10-$it") }
        val next = listOf(shift("2026-12-01"), shift("2026-12-02"))
        val combined = ConsecutiveWorkRule(5, window, emptyList())
        assertEquals("CONSECUTIVE_WORK_LIMIT", combined.bridgedViolation(month, previous + next)?.code)
        assertNull(combined.bridgedViolation(month, previous))
        val rest = RosterRestDay(nurse, month.start, RestDayKind.OF)
        assertNull(ConsecutiveWorkRule(5, window, listOf(rest)).bridgedViolation(month, previous + next))
        assertNull(ConsecutiveWorkRule(5, month, emptyList()).validate(shift("2026-11-01"), previous + next))
    }

    private fun shift(value: String): ShiftAssignment {
        val date = LocalDate.parse(value)
        val start = date.atTime(23, 0).toInstant(ZoneOffset.UTC)
        return ShiftAssignment(UUID.randomUUID(), nurse, date, ShiftKind.NIGHT, start, start.plusSeconds(8 * 3600))
    }
}
