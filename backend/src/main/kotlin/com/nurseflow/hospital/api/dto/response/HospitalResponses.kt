package com.nurseflow.hospital.api.dto.response

import java.time.Instant
import java.util.UUID

data class HospitalResponse(val hospitalId: UUID, val name: String, val timeZone: String, val roles: List<String>)

data class WardResponse(
    val wardId: UUID,
    val hospitalId: UUID,
    val name: String,
    val timeZone: String,
    val createdAt: Instant?,
)

data class ItemsResponse<T>(val items: List<T>)
