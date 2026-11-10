package com.nurseflow.ward.api.dto.response

import java.util.UUID

data class WardMembershipResponse(
    val wardId: UUID,
    val wardName: String,
    val roles: List<String>,
    val canViewWardCalendar: Boolean,
    val hospitalName: String?,
)

data class WardMembershipItems(val items: List<WardMembershipResponse>)
