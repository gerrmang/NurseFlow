package com.nurseflow.ward.api.dto.response

import java.util.UUID

/** OpenAPI `WardMembership`. `hospitalId`·`hospitalName`은 병원이 지정되지 않은 기존 병동에서 null이다. */
data class MyWardResponse(
    val wardId: UUID,
    val wardName: String,
    val timeZone: String,
    val hospitalId: UUID?,
    val hospitalName: String?,
    val roles: List<String>,
    val canViewWardCalendar: Boolean,
)

data class MyWardListResponse(val items: List<MyWardResponse>)
