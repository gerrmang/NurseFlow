package com.nurseflow.ward.api.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class BootstrapWardRequest(
    @field:NotBlank @field:Size(max = 100)
    val name: String,
    @field:NotBlank @field:Size(max = 100)
    val timeZone: String = "Asia/Seoul",
    @field:NotBlank @field:Size(max = 200)
    val bootstrapCode: String,
)
