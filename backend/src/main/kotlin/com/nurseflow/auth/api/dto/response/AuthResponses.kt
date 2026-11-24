package com.nurseflow.auth.api.dto.response

import com.nurseflow.ward.api.dto.response.WardMembershipResponse
import java.time.Instant
import java.util.UUID

data class SessionResponse(
    val tokenType: String = "Bearer",
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val user: SessionUserResponse,
)

data class SessionUserResponse(val userId: UUID, val displayName: String, val newUser: Boolean)

data class CurrentUserResponse(
    val userId: UUID,
    val displayName: String,
    val memberships: List<WardMembershipResponse>,
)

data class WebKakaoAuthorizationResponse(val authorizationUrl: String, val expiresAt: Instant)

/** Web session: the refresh token is only in the HttpOnly cookie, never in this body. */
data class WebSessionResponse(
    val tokenType: String = "Bearer",
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshTokenExpiresAt: Instant,
    val user: SessionUserResponse,
)
