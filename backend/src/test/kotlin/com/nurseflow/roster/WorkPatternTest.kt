package com.nurseflow.roster

import com.nurseflow.roster.domain.model.*
import com.nurseflow.roster.domain.rule.RosterRuleValidator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class WorkPatternTest {
    @Test
    fun `all patterns and shift kinds are validated including OTHER`() {
        val validator = RosterRuleValidator()
        for (pattern in WorkPattern.entries) for (kind in ShiftKind.entries) {
            val expected = pattern == WorkPattern.GENERAL ||
                (pattern == WorkPattern.DAY_EVENING_ONLY && kind in setOf(ShiftKind.DAY, ShiftKind.EVENING)) ||
                (pattern == WorkPattern.NIGHT_ONLY && kind == ShiftKind.NIGHT)
            val shift = ShiftAssignment(null, UUID.randomUUID(), LocalDate.parse("2026-12-31"), kind,
                Instant.parse("2026-12-31T14:00:00Z"), Instant.parse("2026-12-31T22:00:00Z"))
            assertEquals(expected, validator.validate(shift, emptyList(), emptyList(), pattern).isEmpty(), "$pattern $kind")
        }
    }
}
