package com.nurseflow.roster.domain.rule

import com.nurseflow.roster.domain.model.RuleSeverity
import com.nurseflow.roster.domain.model.RuleViolation
import com.nurseflow.roster.domain.model.SupportAllowance
import java.time.LocalDate
import java.util.UUID

/**
 * A nurse works on their own team's slots. Another team's slot needs an active allowance covering that nurse,
 * that team, the slot's work date and its shift type. Slots without a team (wards without teams) are open to
 * everyone. The allowance relaxes only the team check: every other hard rule still applies.
 */
class CrossTeamSupportRule(private val allowances: Collection<SupportAllowance>) {
    fun validate(
        nurseId: UUID, nurseTeamId: UUID?, slotTeamId: UUID?, date: LocalDate, shiftTypeId: UUID,
    ): RuleViolation? {
        if (slotTeamId == null || slotTeamId == nurseTeamId) return null
        if (allowances.any { it.covers(nurseId, slotTeamId, date, shiftTypeId) }) return null
        return RuleViolation(CODE, RuleSeverity.HARD, "관리자가 허용한 팀 간 지원 범위 밖의 배치입니다.", date)
    }

    companion object {
        const val CODE = "CROSS_TEAM_NOT_ALLOWED"
    }
}
