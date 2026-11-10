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

data class WardMemberResult(
    val userId: String,
    val displayName: String,
    val roles: List<WardRole>,
    val active: Boolean,
    val canViewWardCalendar: Boolean,
    val nurseId: UUID?,
)

data class UpdateMembershipCommand(val admin: Boolean?, val active: Boolean?)

data class CreateWardCommand(
    val hospitalSource: String,
    val hospitalExternalId: String,
    val wardName: String,
    val timeZone: String,
)
