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
) {
    val isAdmin: Boolean get() = "WARD_ADMIN" in roles
}
