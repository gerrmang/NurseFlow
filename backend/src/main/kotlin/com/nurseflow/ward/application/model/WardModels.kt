package com.nurseflow.ward.application.model

import java.util.UUID

enum class WardRole { WARD_ADMIN, WARD_MEMBER }

data class WardMembershipResult(
    val wardId: UUID,
    val wardName: String,
    val roles: List<WardRole>,
    val canViewWardCalendar: Boolean,
    val hospitalName: String?,
)

data class CreateWardCommand(
    val hospitalSource: String,
    val hospitalExternalId: String,
    val wardName: String,
    val timeZone: String,
)
