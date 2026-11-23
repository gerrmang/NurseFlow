package com.nurseflow.roster.api.dto.request

import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.error.InvalidRequestCode
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.util.UUID

data class ConsecutiveWorkPolicyRequest(@field:Min(1) @field:Max(31) val maxConsecutiveWorkDays: Int)

@InvalidRequestCode(ErrorCode.INVALID_REST_DAYS)
data class ReplaceRestDaysRequest(@field:Size(max = 10_000) @field:Valid val items: List<RestDayInput>)

data class RestDayInput(
    val nurseId: UUID,
    val date: LocalDate,
    @field:Pattern(regexp = "OF|SOF") val kind: String,
)
