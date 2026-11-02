package com.nurseflow.auth.api.controller

import com.nurseflow.auth.api.dto.request.KakaoLoginRequest
import com.nurseflow.auth.api.dto.request.LogoutRequest
import com.nurseflow.auth.api.dto.request.RefreshSessionRequest
import com.nurseflow.auth.api.dto.response.SessionResponse
import com.nurseflow.auth.api.dto.response.SessionUserResponse
import com.nurseflow.auth.application.model.KakaoLoginCommand
import com.nurseflow.auth.application.model.SessionResult
import com.nurseflow.auth.application.service.AuthService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Shared Android/web session contract. These endpoints are public; everything else under
 * /api/v1 requires the NurseFlow access token returned here.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(private val auth: AuthService) {
    @PostMapping("/kakao/session")
    fun kakaoLogin(@Valid @RequestBody request: KakaoLoginRequest): ResponseEntity<SessionResponse> =
        noStore(auth.loginWithKakao(KakaoLoginCommand(request.idToken, request.nonce)))

    /** Keeps the user signed in: exchanges a refresh token for a new access + refresh token pair. */
    @PostMapping("/session/refresh")
    fun refresh(@Valid @RequestBody request: RefreshSessionRequest): ResponseEntity<SessionResponse> =
        noStore(auth.refresh(request.refreshToken))

    @PostMapping("/logout")
    fun logout(@Valid @RequestBody request: LogoutRequest): ResponseEntity<Void> {
        auth.logout(request.refreshToken)
        return ResponseEntity.noContent().header("Cache-Control", "no-store").build()
    }

    private fun noStore(result: SessionResult) = ResponseEntity.ok()
        .header("Cache-Control", "no-store")
        .body(
            SessionResponse(
                accessToken = result.accessToken,
                accessTokenExpiresAt = result.accessTokenExpiresAt,
                refreshToken = result.refreshToken,
                refreshTokenExpiresAt = result.refreshTokenExpiresAt,
                user = SessionUserResponse(result.user.userId, result.user.displayName, result.user.newUser),
            ),
        )
}
