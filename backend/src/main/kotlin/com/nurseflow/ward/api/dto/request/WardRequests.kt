package com.nurseflow.ward.api.dto.request

import com.nurseflow.hospital.application.service.HospitalDatasetImporter
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

data class CreateWardRequest(
    @field:NotNull @field:Valid
    val hospital: HospitalSelection?,
    @field:NotBlank @field:Size(max = 100)
    val wardName: String,
    @field:NotBlank @field:Size(max = 100)
    val timeZone: String = "Asia/Seoul",
)

/** The `source` and `externalId` of a hospital search result, as returned. */
data class HospitalSelection(
    @field:NotBlank @field:Pattern(regexp = HospitalDatasetImporter.SOURCE)
    val source: String,
    @field:NotBlank @field:Size(max = 255)
    val externalId: String,
)
