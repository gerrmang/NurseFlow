package com.nurseflow.offrequest.api.dto.request

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.UUID

/** `nurseId` is set only by a ward admin entering a request on a nurse's behalf. */
data class SubmitOffRequestRequest(val date: LocalDate, val nurseId: UUID? = null)

/** Whole JSON numbers only: a fraction or a numeric string is rejected rather than truncated. */
data class OffRequestPolicyRequest(val monthlyLimit: JsonNode, val deadlineDay: JsonNode) {
    fun limit(): Int = monthlyLimit.wholeNumber()
    fun day(): Int = deadlineDay.wholeNumber()

    private fun JsonNode.wholeNumber(): Int {
        if (!isIntegralNumber || !canConvertToInt()) throw BusinessException(ErrorCode.INVALID_REQUEST)
        return intValue()
    }
}
