package com.nurseflow.offrequest

import com.nurseflow.offrequest.domain.OffRequestPolicy
import com.nurseflow.roster.domain.model.ShiftAssignment
import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.domain.rule.ConfirmedOffRequestRule
import com.nurseflow.roster.domain.rule.RosterRuleValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

class OffRequestPolicyTest {
    private val seoul = ZoneId.of("Asia/Seoul")

    @Test
    fun `deadline closes at the end of the deadline day of the previous month in the ward zone`() {
        val policy = OffRequestPolicy(4, 15)
        assertEquals(Instant.parse("2026-11-15T15:00:00Z"), policy.deadline(YearMonth.of(2026, 12), seoul))
        // January's deadline falls in December of the previous year.
        assertEquals(Instant.parse("2026-12-15T15:00:00Z"), policy.deadline(YearMonth.of(2027, 1), seoul))
        assertEquals(Instant.parse("2027-02-28T15:00:00Z"), OffRequestPolicy(4, 28).deadline(YearMonth.of(2027, 3), seoul))
        assertEquals(Instant.parse("2026-11-16T00:00:00Z"), policy.deadline(YearMonth.of(2026, 12), ZoneId.of("UTC")))
    }

    @Test
    fun `settings outside their ranges are refused`() {
        assertEquals(OffRequestPolicy(OffRequestPolicy.DEFAULT_MONTHLY_LIMIT, OffRequestPolicy.DEFAULT_DEADLINE_DAY), OffRequestPolicy(4, 15))
        assertThrows(IllegalArgumentException::class.java) { OffRequestPolicy(-1, 15) }
        assertThrows(IllegalArgumentException::class.java) { OffRequestPolicy(4, 29) }
    }

    @Test
    fun `a confirmed day blocks shifts starting on it but not a night ending on it`() {
        val nurse = UUID.randomUUID()
        val day = LocalDate.parse("2026-12-02")
        val rule = ConfirmedOffRequestRule(listOf(nurse to day))
        fun shift(date: LocalDate, kind: ShiftKind, startHour: Int, hours: Long, who: UUID = nurse): ShiftAssignment {
            val start = date.atTime(startHour, 0).atZone(seoul).toInstant()
            return ShiftAssignment(UUID.randomUUID(), who, date, kind, start, start.plusSeconds(hours * 3600))
        }
        val validator = RosterRuleValidator()
        val blocked = validator.validate(shift(day, ShiftKind.DAY, 7, 8), emptyList(), emptyList(), confirmedOffs = rule)
        assertTrue(blocked.any { it.code == "CONFIRMED_OFF_REQUEST" && it.affectedDate == day })
        assertTrue(validator.validate(shift(day.minusDays(1), ShiftKind.NIGHT, 19, 12), emptyList(), emptyList(), confirmedOffs = rule).isEmpty())
        assertTrue(validator.validate(shift(day, ShiftKind.DAY, 7, 8, UUID.randomUUID()), emptyList(), emptyList(), confirmedOffs = rule).isEmpty())
    }
}
