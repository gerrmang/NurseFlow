package com.nurseflow.roster.api.dto.response

import java.time.LocalDate
import java.util.UUID

data class ReadinessItemResponse(
    val key: String,
    val status: String,
    val blocking: Boolean,
    val message: String,
    val missing: List<String>,
)

data class WardSetupReadinessResponse(
    val wardId: UUID,
    val ready: Boolean,
    val completedCount: Int,
    val totalCount: Int,
    val items: List<ReadinessItemResponse>,
)

data class SeniorCoverageResponse(
    val teamId: UUID,
    val teamName: String,
    /** DAY, EVENING, NIGHT, or null for the whole team. */
    val shiftKind: String?,
    val requiredSeniorSlots: Int,
    val availableSeniors: Int,
    val seniorShiftCapacity: Int,
    val shortage: Boolean,
    val reasons: List<String>,
    val firstShortDate: LocalDate?,
)

data class GenerationPrecheckResponse(
    val rosterId: UUID,
    val wardId: UUID,
    val month: String,
    val revision: Int,
    val ready: Boolean,
    val items: List<ReadinessItemResponse>,
    val maxShiftsPerNurse: Int,
    val seniorCoverage: List<SeniorCoverageResponse>,
)
