package com.nurseflow.ward.api.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** Any signed-in user may create a ward; the creator becomes its first WARD_ADMIN. */
data class CreateWardRequest(
    @field:NotBlank @field:Size(max = 100)
    val name: String,
    @field:NotBlank @field:Size(max = 100)
    val timeZone: String = "Asia/Seoul",
    /** The admin's own career start (YYYY-MM) and rank, collected at ward creation. */
    @field:Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$")
    val careerStartedOn: String,
    @field:NotBlank @field:Size(max = 30)
    val rank: String,
)
