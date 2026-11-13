package com.nurseflow.policy.api.dto.request

import com.nurseflow.roster.domain.model.ShiftKind
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

const val HOUR_MINUTE_PATTERN = "^([01]\\d|2[0-3]):[0-5]\\d$"

data class ShiftTypeRequest(
    @field:Pattern(regexp = "^[A-Z0-9_]{1,32}$")
    val code: String,
    @field:NotBlank @field:Size(max = 64)
    val displayName: String,
    val kind: ShiftKind,
    @field:Pattern(regexp = HOUR_MINUTE_PATTERN)
    val startTime: String,
    @field:Pattern(regexp = HOUR_MINUTE_PATTERN)
    val endTime: String,
    val spansMidnight: Boolean,
    @field:Min(0) @field:Max(240)
    val breakMinutes: Int? = null,
    @field:Min(1) @field:Max(1440)
    val recognizedMinutes: Int? = null,
    @field:Min(0) @field:Max(1440)
    val targetCreditMinutes: Int? = null,
    val active: Boolean? = null,
)
