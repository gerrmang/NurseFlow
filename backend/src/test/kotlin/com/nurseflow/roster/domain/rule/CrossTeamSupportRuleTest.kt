package com.nurseflow.roster.domain.rule

import com.nurseflow.roster.domain.model.SupportAllowance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class CrossTeamSupportRuleTest {
    private val nurse = UUID.randomUUID()
    private val home = UUID.randomUUID()
    private val target = UUID.randomUUID()
    private val day = UUID.randomUUID()
    private val night = UUID.randomUUID()
    private val start = LocalDate.parse("2026-12-10")
    private val end = LocalDate.parse("2026-12-12")

    private fun allowance(shifts: Set<UUID> = emptySet(), revokedAt: Instant? = null) = SupportAllowance(
        UUID.randomUUID(), UUID.randomUUID(), nurse, target, start, end, shifts, Instant.EPOCH, "admin", revokedAt,
    )

    private fun CrossTeamSupportRule.code(date: LocalDate, shift: UUID = day, slotTeam: UUID? = target, nurseTeam: UUID? = home) =
        validate(nurse, nurseTeam, slotTeam, date, shift)?.code

    @Test
    fun `own team and team-less slots never need an allowance`() {
        val rule = CrossTeamSupportRule(emptyList())
        assertNull(rule.code(start, slotTeam = home))
        assertNull(rule.code(start, slotTeam = null))
        assertEquals("CROSS_TEAM_NOT_ALLOWED", rule.code(start))
        // A nurse without a team is support everywhere a team is set.
        assertEquals("CROSS_TEAM_NOT_ALLOWED", rule.code(start, nurseTeam = null))
    }

    @Test
    fun `allowance covers its inclusive dates its shift types and its team only`() {
        val rule = CrossTeamSupportRule(listOf(allowance(setOf(day))))
        assertNull(rule.code(start))
        assertNull(rule.code(end))
        assertEquals("CROSS_TEAM_NOT_ALLOWED", rule.code(start.minusDays(1)))
        assertEquals("CROSS_TEAM_NOT_ALLOWED", rule.code(end.plusDays(1)))
        assertEquals("CROSS_TEAM_NOT_ALLOWED", rule.code(start, shift = night))
        assertEquals("CROSS_TEAM_NOT_ALLOWED", rule.code(start, slotTeam = UUID.randomUUID()))
        assertEquals("CROSS_TEAM_NOT_ALLOWED",
            CrossTeamSupportRule(listOf(allowance())).validate(UUID.randomUUID(), home, target, start, day)?.code)
    }

    @Test
    fun `empty shift list allows every shift and a revoked allowance allows nothing`() {
        assertNull(CrossTeamSupportRule(listOf(allowance())).code(start, shift = night))
        assertEquals("CROSS_TEAM_NOT_ALLOWED",
            CrossTeamSupportRule(listOf(allowance(revokedAt = Instant.EPOCH))).code(start))
    }
}
