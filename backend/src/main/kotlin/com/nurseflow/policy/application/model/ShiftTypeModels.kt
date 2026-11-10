package com.nurseflow.policy.application.model

import com.nurseflow.roster.domain.model.ShiftKind
import java.time.LocalTime
import java.util.UUID

/** Stored shift type. Null recognized/target (rows created before A8) mean the derived defaults. */
data class ShiftTypeRow(
    val id: UUID,
    val wardId: UUID,
    val code: String,
    val displayName: String,
    val kind: ShiftKind,
    val start: LocalTime,
    val end: LocalTime,
    val spansMidnight: Boolean,
    val active: Boolean,
    val breakMinutes: Int,
    val recognizedMinutes: Int?,
    val targetCreditMinutes: Int?,
)

data class ShiftTypeCommand(
    val code: String,
    val displayName: String,
    val kind: ShiftKind,
    val start: LocalTime,
    val end: LocalTime,
    val spansMidnight: Boolean,
    val breakMinutes: Int?,
    val recognizedMinutes: Int?,
    val targetCreditMinutes: Int?,
    val active: Boolean?,
)

data class ShiftTypeResult(
    val id: UUID,
    val code: String,
    val displayName: String,
    val kind: ShiftKind,
    val start: LocalTime,
    val end: LocalTime,
    val spansMidnight: Boolean,
    val durationMinutes: Int,
    val breakMinutes: Int,
    val recognizedMinutes: Int,
    val targetCreditMinutes: Int,
    val active: Boolean,
)

data class ShiftTypeListResult(val items: List<ShiftTypeResult>, val policyRevision: Int)
