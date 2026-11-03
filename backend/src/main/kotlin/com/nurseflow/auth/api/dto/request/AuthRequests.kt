package com.nurseflow.auth.api.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

/** Kakao OIDC ID token from the Android SDK or the web login, plus the nonce the client sent (if any). */
data class KakaoLoginRequest(
    @field:NotBlank @field:Size(max = 8192)
    val idToken: String,
    @field:Size(min = 1, max = 255)
    val nonce: String? = null,
)

data class RefreshSessionRequest(
    @field:Pattern(regexp = REFRESH_TOKEN_PATTERN)
    val refreshToken: String,
)

data class LogoutRequest(
    @field:Pattern(regexp = REFRESH_TOKEN_PATTERN)
    val refreshToken: String,
)

/** 32 random bytes, base64url without padding. */
const val REFRESH_TOKEN_PATTERN = "^[A-Za-z0-9_-]{43}$"
