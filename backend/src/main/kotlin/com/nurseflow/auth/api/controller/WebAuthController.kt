package com.nurseflow.auth.api.controller

import com.nurseflow.auth.api.dto.request.WebKakaoAuthorizationRequest
import com.nurseflow.auth.api.dto.request.WebKakaoCodeRequest
import com.nurseflow.auth.api.dto.response.SessionUserResponse
import com.nurseflow.auth.api.dto.response.WebKakaoAuthorizationResponse
import com.nurseflow.auth.api.dto.response.WebSessionResponse
import com.nurseflow.auth.application.model.SessionResult
import com.nurseflow.auth.application.service.WebLoginService
import com.nurseflow.auth.config.AuthProperties
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseCookie
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

/**
 * Browser login and session. The refresh token is kept in an HttpOnly, Secure, SameSite=Strict
 * cookie limited to /api/v1/auth/web, out of reach of page scripts. The access token is returned
 * in the body and kept in memory by the web app. Every route here passes WebClientRequestGuard.
 */
@RestController
@RequestMapping("/api/v1/auth/web")
class WebAuthController(private val web: WebLoginService, private val properties: AuthProperties) {
    private val cookies get() = properties.web

    @PostMapping("/kakao/authorization")
    fun startKakaoLogin(@Valid @RequestBody request: WebKakaoAuthorizationRequest): ResponseEntity<WebKakaoAuthorizationResponse> {
        val started = web.start(request.redirectUri)
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
            .header(HttpHeaders.SET_COOKIE, cookie(cookies.loginCookieName, started.browserBinding, cookies.loginAttemptTtl))
            .body(WebKakaoAuthorizationResponse(started.authorizationUrl, started.expiresAt))
    }

    @PostMapping("/kakao/code")
    fun completeKakaoLogin(
        @Valid @RequestBody request: WebKakaoCodeRequest,
        http: HttpServletRequest,
    ): ResponseEntity<WebSessionResponse> {
        val result = web.complete(request.code, request.state, readCookie(http, cookies.loginCookieName))
        return session(result, clearLoginCookie = true)
    }

    @PostMapping("/session/refresh")
    fun refresh(http: HttpServletRequest): ResponseEntity<WebSessionResponse> =
        session(web.refresh(readCookie(http, cookies.refreshCookieName)), clearLoginCookie = false)

    @PostMapping("/logout")
    fun logout(http: HttpServletRequest): ResponseEntity<Void> {
        web.logout(readCookie(http, cookies.refreshCookieName))
        return ResponseEntity.noContent()
            .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
            .header(HttpHeaders.SET_COOKIE, cookie(cookies.refreshCookieName, "", Duration.ZERO))
            .build()
    }

    private fun session(result: SessionResult, clearLoginCookie: Boolean): ResponseEntity<WebSessionResponse> {
        val refreshMaxAge = Duration.between(Instant.now(), result.refreshTokenExpiresAt).coerceAtLeast(Duration.ZERO)
        val builder = ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, NO_STORE)
            .header(HttpHeaders.SET_COOKIE, cookie(cookies.refreshCookieName, result.refreshToken, refreshMaxAge))
        if (clearLoginCookie) builder.header(HttpHeaders.SET_COOKIE, cookie(cookies.loginCookieName, "", Duration.ZERO))
        return builder.body(
            WebSessionResponse(
                accessToken = result.accessToken,
                accessTokenExpiresAt = result.accessTokenExpiresAt,
                refreshTokenExpiresAt = result.refreshTokenExpiresAt,
                user = SessionUserResponse(result.user.userId, result.user.displayName, result.user.newUser),
            ),
        )
    }

    private fun cookie(name: String, value: String, maxAge: Duration): String = ResponseCookie.from(name, value)
        .httpOnly(true)
        .secure(cookies.cookieSecure)
        .sameSite("Strict")
        .path(AuthProperties.Web.COOKIE_PATH)
        .maxAge(maxAge)
        .build()
        .toString()

    private fun readCookie(http: HttpServletRequest, name: String): String? =
        http.cookies?.firstOrNull { it.name == name }?.value?.takeIf { it.isNotBlank() }

    private companion object {
        const val NO_STORE = "no-store"
    }
}
