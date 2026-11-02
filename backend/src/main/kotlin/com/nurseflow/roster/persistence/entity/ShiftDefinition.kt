package com.nurseflow.roster.persistence.entity

import com.nurseflow.roster.domain.model.ShiftKind
import java.time.LocalTime

data class ShiftDefinition(
    val code: String,
    val kind: ShiftKind,
    val start: LocalTime,
    val end: LocalTime,
    val spansMidnight: Boolean,
)
