package com.nurseflow.roster.domain.rule

import com.nurseflow.roster.domain.model.RuleSeverity
import com.nurseflow.roster.domain.model.RuleViolation
import com.nurseflow.roster.domain.model.ShiftAssignment
import java.time.LocalDate
import java.util.UUID

/**
 * Preferred days off that a ward admin confirmed (A12). Like an OF day, a confirmed date blocks any shift that
 * starts on it for that nurse; a Night that began the evening before may still end on it. Submitted (unconfirmed)
 * requests are preferences and never reach this rule.
 */
class ConfirmedOffRequestRule(confirmed: Collection<Pair<UUID, LocalDate>>) {
    private val days: Set<Pair<UUID, LocalDate>> = confirmed.toSet()

    fun validate(candidate: ShiftAssignment): RuleViolation? =
        if (candidate.nurseId to candidate.shiftDate in days) {
            RuleViolation(
                CODE, RuleSeverity.HARD, "관리자가 확정한 희망 오프 날짜에는 근무를 배정할 수 없습니다.", candidate.shiftDate,
            )
        } else {
            null
        }

    companion object {
        const val CODE = "CONFIRMED_OFF_REQUEST"
        val NONE = ConfirmedOffRequestRule(emptyList())
    }
}
