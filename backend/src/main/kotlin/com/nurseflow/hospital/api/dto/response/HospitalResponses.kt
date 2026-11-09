package com.nurseflow.hospital.api.dto.response

data class HospitalCandidateResponse(
    val source: String,
    val externalId: String,
    val name: String,
    val address: String,
    val category: String?,
)

data class HospitalSearchPageResponse(
    val items: List<HospitalCandidateResponse>,
    val page: Int,
    val size: Int,
    val totalCount: Int,
    val attribution: String,
)
