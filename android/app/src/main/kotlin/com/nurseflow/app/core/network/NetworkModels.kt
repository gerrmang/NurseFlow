package com.nurseflow.app.core.network

import kotlinx.serialization.Serializable

// Mirrors docs/openapi.yaml. Unknown fields are ignored so additive server changes don't break the app.

@Serializable
data class Items<T>(val items: List<T>)

@Serializable
data class ApiProblemDto(val status: Int? = null, val code: String? = null, val detail: String? = null)

// ── Auth ──

@Serializable
data class KakaoLoginBody(val idToken: String, val nonce: String? = null)

@Serializable
data class RefreshTokenBody(val refreshToken: String)

@Serializable
data class SessionDto(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String? = null,
    val refreshTokenExpiresAt: String,
    val user: SessionUserDto,
)

@Serializable
data class SessionUserDto(val userId: String, val displayName: String, val newUser: Boolean = false)

@Serializable
data class CurrentUserDto(val userId: String, val displayName: String, val memberships: List<MembershipDto>)

@Serializable
data class MembershipDto(
    val wardId: String,
    val wardName: String,
    val roles: List<String>,
    val canViewWardCalendar: Boolean,
    val hospitalName: String? = null,
) {
    val isAdmin: Boolean get() = "WARD_ADMIN" in roles
}

// ── Published rosters (docs/openapi.yaml: PublishedWardCalendar / PublishedPersonalCalendar) ──
// Only fields the screen needs. Absences, drafts and admin memo are never part of this contract.

@Serializable
data class PublishedWardCalendarDto(
    val month: String,
    val timeZone: String,
    val slots: List<StaffingSlotDto>,
)

@Serializable
data class StaffingSlotDto(
    val date: String,
    val shiftTypeId: String,
    val startAt: String,
    val endAt: String,
    val status: String,
    val assignment: AssignmentDto? = null,
)

@Serializable
data class AssignmentDto(val displayName: String)

@Serializable
data class PublishedPersonalCalendarDto(
    val month: String,
    val timeZone: String,
    val assignments: List<PersonalAssignmentDto>,
)

@Serializable
data class PersonalAssignmentDto(
    val date: String,
    val shiftType: String,
    val startsAt: String,
    val endsAt: String,
)
