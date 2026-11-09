package com.nurseflow.auth.api.dto.request

import com.nurseflow.common.security.OpaqueToken
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
const val REFRESH_TOKEN_PATTERN = OpaqueToken.PATTERN

/** Web login start: the exact callback URI the web app will receive the code on. */
data class WebKakaoAuthorizationRequest(
    @field:NotBlank @field:Size(max = 2048)
    val redirectUri: String,
)

/** Web login callback: the code and state Kakao appended to the redirect URI. */
data class WebKakaoCodeRequest(
    @field:NotBlank @field:Size(max = 2048)
    val code: String,
    @field:Pattern(regexp = OpaqueToken.PATTERN)
    val state: String,
)
