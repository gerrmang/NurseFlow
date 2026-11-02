package com.nurseflow.roster.domain.model

import java.time.LocalDate

enum class RuleSeverity {
    HARD,
    SOFT,
}

data class RuleViolation(
    val code: String,
    val severity: RuleSeverity,
    val message: String,
    val affectedDate: LocalDate? = null,
)
