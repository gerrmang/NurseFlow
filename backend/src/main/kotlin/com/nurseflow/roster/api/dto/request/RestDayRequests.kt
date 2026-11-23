package com.nurseflow.roster.api.dto.request

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.error.InvalidRequestCode
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.UUID

data class ConsecutiveWorkPolicyRequest(val maxConsecutiveWorkDays: JsonNode) {
    fun integerLimit(): Int {
        if (!maxConsecutiveWorkDays.isIntegralNumber || !maxConsecutiveWorkDays.canConvertToInt()) {
            throw BusinessException(ErrorCode.INVALID_REQUEST)
        }
        return maxConsecutiveWorkDays.intValue()
    }
}

@InvalidRequestCode(ErrorCode.INVALID_REST_DAYS)
data class ReplaceRestDaysRequest(@field:Size(max = 10_000) @field:Valid val items: List<RestDayInput>)

data class RestDayInput(
    val nurseId: UUID,
    val date: LocalDate,
    @field:Pattern(regexp = "OF|SOF") val kind: String,
)
