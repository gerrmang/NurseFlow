package com.nurseflow.roster.api.dto.response

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SupportAllowanceResponse(
    val allowanceId: UUID,
    val nurseId: UUID,
    val teamId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val shiftTypeIds: List<UUID>,
    val status: String,
    val createdAt: Instant,
    val revokedAt: Instant?,
)

data class SupportAllowanceRevocationResponse(
    val allowance: SupportAllowanceResponse,
    val flaggedDraftCount: Int,
)
