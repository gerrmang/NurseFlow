package com.nurseflow.policy.api.dto.response

import java.util.UUID

data class ShiftTypeResponse(
    val shiftTypeId: UUID,
    val code: String,
    val displayName: String,
    val kind: String,
    val startTime: String,
    val endTime: String,
    val spansMidnight: Boolean,
    val durationMinutes: Int,
    val breakMinutes: Int,
    val recognizedMinutes: Int,
    val targetCreditMinutes: Int,
    val active: Boolean,
)

data class ShiftTypeItems(val items: List<ShiftTypeResponse>, val policyRevision: Int)
