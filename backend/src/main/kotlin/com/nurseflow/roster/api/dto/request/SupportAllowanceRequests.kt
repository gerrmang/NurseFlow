package com.nurseflow.roster.api.dto.request

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

/** An empty or missing [shiftTypeIds] allows every shift type within the dates. */
data class CreateSupportAllowanceRequest(
    val nurseId: UUID,
    val teamId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate,
    @field:Size(max = 20) val shiftTypeIds: List<UUID?> = emptyList(),
) {
    /** JSON `[null]` binds here: rejected as a malformed request rather than reaching the service. */
    fun shiftTypes(): List<UUID> = shiftTypeIds.map { it ?: throw BusinessException(ErrorCode.INVALID_REQUEST) }
}
