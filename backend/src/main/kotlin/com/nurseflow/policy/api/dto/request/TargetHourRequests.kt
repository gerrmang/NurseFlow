package com.nurseflow.policy.api.dto.request

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.error.InvalidRequestCode
import com.nurseflow.policy.domain.CalendarDay
import com.nurseflow.policy.domain.CalendarDayKind
import com.nurseflow.policy.domain.TargetHourPolicy
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import tools.jackson.databind.JsonNode
import java.time.LocalDate

/**
 * Minutes arrive as raw JSON so `480.5` or `"480"` is refused instead of being coerced to an integer.
 * A missing or null required value is refused with the same code as an out-of-range one. The Night-dedicated
 * values may be null or absent: they then follow the general values.
 */
data class TargetHourPolicyRequest(
    val dailyStandardMinutes: JsonNode? = null,
    val leaveCreditMinutes: JsonNode? = null,
    val halfDayCreditMinutes: JsonNode? = null,
    val sofCreditMinutes: JsonNode? = null,
    val nightDedicatedTargetMinutes: JsonNode? = null,
    val nightDedicatedNightCreditMinutes: JsonNode? = null,
) {
    fun toPolicy() = TargetHourPolicy(
        required(dailyStandardMinutes), required(leaveCreditMinutes), required(halfDayCreditMinutes),
        required(sofCreditMinutes), optional(nightDedicatedTargetMinutes), optional(nightDedicatedNightCreditMinutes),
    )

    private fun required(node: JsonNode?): Int = optional(node) ?: throw BusinessException(ErrorCode.INVALID_TARGET_HOUR_POLICY)

    private fun optional(node: JsonNode?): Int? {
        if (node == null || node.isNull || node.isMissingNode) return null
        if (!node.isIntegralNumber || !node.canConvertToInt()) throw BusinessException(ErrorCode.INVALID_TARGET_HOUR_POLICY)
        return node.intValue()
    }
}

@InvalidRequestCode(ErrorCode.INVALID_HOLIDAY_CALENDAR)
data class ReplaceHolidayCalendarRequest(@field:Size(max = 31) @field:Valid val days: List<HolidayCalendarDayInput>) {
    fun toDays() = days.map { CalendarDay(it.date, CalendarDayKind.valueOf(it.kind), it.name) }
}

data class HolidayCalendarDayInput(
    val date: LocalDate,
    @field:Pattern(regexp = "PUBLIC_HOLIDAY|SUBSTITUTE_HOLIDAY|WARD_REST_DAY|WARD_WORKDAY") val kind: String,
    @field:Size(max = 50) val name: String? = null,
)
