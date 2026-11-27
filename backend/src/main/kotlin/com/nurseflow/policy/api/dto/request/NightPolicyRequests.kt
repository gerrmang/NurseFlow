package com.nurseflow.policy.api.dto.request

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.policy.domain.NightPolicy
import tools.jackson.databind.JsonNode

/**
 * Every value is required and arrives as raw JSON, so `6.5` or `"6"` is refused instead of being coerced. A missing
 * or null value is refused with the same code as an out-of-range one.
 */
data class NightPolicyRequest(
    val sofNightThreshold: JsonNode? = null,
    val sofDays: JsonNode? = null,
    val dedicatedHeadcount: JsonNode? = null,
    val dedicatedRotationMonths: JsonNode? = null,
    val dedicatedMinNights: JsonNode? = null,
) {
    fun toPolicy() = NightPolicy(
        required(sofNightThreshold), required(sofDays), required(dedicatedHeadcount),
        required(dedicatedRotationMonths), required(dedicatedMinNights),
    )

    private fun required(node: JsonNode?): Int {
        if (node == null || node.isNull || node.isMissingNode || !node.isIntegralNumber || !node.canConvertToInt()) {
            throw BusinessException(ErrorCode.INVALID_NIGHT_POLICY)
        }
        return node.intValue()
    }
}
