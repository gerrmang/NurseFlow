package com.nurseflow.ward.application.model

import java.util.UUID

enum class WardRole { WARD_ADMIN, WARD_MEMBER }

data class WardMembershipResult(
    val wardId: UUID,
    val wardName: String,
    val roles: List<WardRole>,
    val canViewWardCalendar: Boolean,
)
