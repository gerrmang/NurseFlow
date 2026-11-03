package com.nurseflow.auth.application.model

import java.time.Instant
import java.util.UUID

/** Identity proven by a verified Kakao ID token. `displayName` is null without profile consent. */
data class VerifiedIdentity(val provider: String, val subject: String, val displayName: String?)

data class KakaoLoginCommand(val idToken: String, val nonce: String?)

data class SessionUser(val userId: UUID, val displayName: String, val newUser: Boolean)

data class SessionResult(
    val accessToken: String,
    val accessTokenExpiresAt: Instant,
    val refreshToken: String,
    val refreshTokenExpiresAt: Instant,
    val user: SessionUser,
)

data class IssuedAccessToken(val value: String, val expiresAt: Instant)
