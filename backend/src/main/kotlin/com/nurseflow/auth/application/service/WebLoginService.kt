package com.nurseflow.auth.application.service

import com.nurseflow.auth.application.model.SessionResult
import com.nurseflow.auth.application.model.WebLoginStart
import com.nurseflow.auth.config.AuthProperties
import com.nurseflow.auth.persistence.entity.DbWebLoginAttempt
import com.nurseflow.auth.persistence.repository.WebLoginAttemptJdbcRepository
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.security.OpaqueToken
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Web Kakao login (OAuth authorization code + OIDC). The server owns `state` and `nonce`:
 * - `state` is single use, expires, and is bound to the browser that started the login through
 *   an HttpOnly cookie, which blocks login CSRF (an attacker's code landing in a victim's browser).
 * - `nonce` is checked against the ID token returned by Kakao's token endpoint.
 * The redirect URI must be on the configured allow-list and is reused verbatim for the exchange.
 */
@Service
class WebLoginService(
    private val properties: AuthProperties,
    private val attempts: WebLoginAttemptJdbcRepository,
    private val kakaoTokens: KakaoTokenClient,
    private val verifier: KakaoIdTokenVerifier,
    private val auth: AuthService,
    private val transactions: TransactionTemplate,
) {
    private val kakao get() = properties.kakao

    fun start(redirectUri: String): WebLoginStart {
        requireConfigured()
        if (redirectUri !in kakao.webRedirectUris) throw BusinessException(ErrorCode.INVALID_REDIRECT_URI)
        val now = Instant.now()
        val expiresAt = now.plus(properties.web.loginAttemptTtl)
        val state = OpaqueToken.generate()
        val nonce = OpaqueToken.generate()
        val binding = OpaqueToken.generate()
        transactions.executeWithoutResult {
            attempts.deleteCreatedBefore(now.minus(CLEANUP_AFTER))
            attempts.insert(
                UUID.randomUUID(), OpaqueToken.sha256Hex(state), OpaqueToken.sha256Hex(binding),
                nonce, redirectUri, now, expiresAt,
            )
        }
        // URI variables are strictly encoded, so redirect_uri and scope are fully percent-encoded.
        val params = linkedMapOf(
            "response_type" to "code",
            "client_id" to kakao.restApiKey.trim(),
            "redirect_uri" to redirectUri,
            "state" to state,
            "nonce" to nonce,
        )
        kakao.scope.trim().takeIf { it.isNotEmpty() }?.let { params["scope"] = it }
        val url = UriComponentsBuilder.fromUriString(kakao.authorizeUri)
            .apply { params.keys.forEach { queryParam(it, "{$it}") } }
            .encode()
            .buildAndExpand(params)
            .toUriString()
        return WebLoginStart(url, binding, expiresAt)
    }

    /**
     * Consumes the login attempt first (even if the code exchange then fails, the state cannot be
     * replayed), then exchanges the code and verifies the ID token outside any transaction.
     */
    fun complete(code: String, state: String, browserBinding: String?): SessionResult {
        requireConfigured()
        if (browserBinding.isNullOrBlank()) throw BusinessException(ErrorCode.INVALID_LOGIN_STATE)
        val attempt = transactions.execute { consume(state, browserBinding, Instant.now()) }!!
        if (attempt.redirectUri !in kakao.webRedirectUris) throw BusinessException(ErrorCode.INVALID_REDIRECT_URI)
        val idToken = kakaoTokens.exchangeForIdToken(code, attempt.redirectUri)
        val identity = verifier.verifyWebLogin(idToken, attempt.nonce)
        return auth.loginWithVerifiedIdentity(identity)
    }

    fun refresh(refreshToken: String?): SessionResult = auth.refresh(
        refreshToken?.takeIf { OpaqueToken.PATTERN.toRegex().matches(it) }
            ?: throw BusinessException(ErrorCode.INVALID_REFRESH_TOKEN),
    )

    /** Idempotent, like the Android logout. */
    fun logout(refreshToken: String?) {
        refreshToken?.takeIf { OpaqueToken.PATTERN.toRegex().matches(it) }?.let(auth::logout)
    }

    private fun consume(state: String, browserBinding: String, now: Instant): DbWebLoginAttempt {
        val attempt = attempts.lockByStateHash(OpaqueToken.sha256Hex(state))
            ?: throw BusinessException(ErrorCode.INVALID_LOGIN_STATE)
        val sameBrowser = MessageDigest.isEqual(
            attempt.browserBindingHash.toByteArray(),
            OpaqueToken.sha256Hex(browserBinding).toByteArray(),
        )
        if (!sameBrowser || attempt.consumedAt != null || !now.isBefore(attempt.expiresAt)) {
            throw BusinessException(ErrorCode.INVALID_LOGIN_STATE)
        }
        attempts.markConsumed(attempt.id, now)
        return attempt
    }

    private fun requireConfigured() {
        if (!kakao.webLoginConfigured) throw BusinessException(ErrorCode.IDENTITY_PROVIDER_NOT_CONFIGURED)
    }

    companion object {
        /** Rows are useless after expiry; keep a day for diagnosis, then delete on the next login start. */
        private val CLEANUP_AFTER: Duration = Duration.ofDays(1)
    }
}
