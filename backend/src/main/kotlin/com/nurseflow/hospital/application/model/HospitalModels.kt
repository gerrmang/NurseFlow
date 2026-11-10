package com.nurseflow.hospital.application.model

/** One normalized search hit. `externalId` is the source's institution id (HIRA: encrypted ykiho). */
data class HospitalCandidate(
    val source: String,
    val externalId: String,
    val name: String,
    val address: String,
    val category: String?,
)

data class HospitalSearchResult(
    val items: List<HospitalCandidate>,
    val page: Int,
    val size: Int,
    val totalCount: Int,
    val attribution: String,
)
