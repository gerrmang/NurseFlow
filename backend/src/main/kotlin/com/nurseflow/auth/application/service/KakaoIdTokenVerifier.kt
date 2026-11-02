package com.nurseflow.auth.application.service

import com.nurseflow.auth.application.model.VerifiedIdentity
import com.nurseflow.auth.config.AuthProperties
import com.nurseflow.auth.config.jwtCheck
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtException
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant

/**
 * Verifies a Kakao OIDC ID token obtained by the Android SDK or the web login:
 * RS256 signature against Kakao's JWKS, issuer, audience (one of our Kakao app keys),
 * expiry, issue time and optional nonce. The token itself is never stored or logged.
 */
@Component
class KakaoIdTokenVerifier(private val properties: AuthProperties) {
    private val kakao get() = properties.kakao

    private val decoder: JwtDecoder by lazy {
        NimbusJwtDecoder.withJwkSetUri(kakao.jwkSetUri)
            .jwsAlgorithm(SignatureAlgorithm.RS256)
            .build()
            .apply {
                setJwtValidator(DelegatingOAuth2TokenValidator(
                    JwtValidators.createDefaultWithIssuer(kakao.issuer),
                    jwtCheck("audience") { jwt -> jwt.audience.orEmpty().any { it in kakao.audiences } },
                    jwtCheck("subject") { jwt -> jwt.subject.let { !it.isNullOrBlank() && it.length <= 255 } },
                    jwtCheck("issued_at") { jwt ->
                        jwt.issuedAt?.isAfter(Instant.now().minus(kakao.maxIdTokenAge)) == true
                    },
                ))
            }
    }

    fun verify(idToken: String, expectedNonce: String?): VerifiedIdentity {
        if (kakao.audiences.none { it.isNotBlank() }) throw BusinessException(ErrorCode.IDENTITY_PROVIDER_NOT_CONFIGURED)
        val jwt = try {
            decoder.decode(idToken)
        } catch (_: BadJwtException) {
            throw BusinessException(ErrorCode.INVALID_ID_TOKEN)
        } catch (_: JwtException) {
            // JWKS could not be fetched or parsed: a provider problem, not a client error.
            throw BusinessException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE)
        }
        checkNonce(jwt, expectedNonce)
        return VerifiedIdentity(PROVIDER, requireNotNull(jwt.subject), nickname(jwt))
    }

    /** When the client sent a nonce in the authorization request it must come back unchanged. */
    private fun checkNonce(jwt: Jwt, expected: String?) {
        val actual = jwt.getClaimAsString("nonce")
        if (expected == null && actual == null) return
        if (expected == null || actual == null ||
            !MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
        ) {
            throw BusinessException(ErrorCode.INVALID_ID_TOKEN)
        }
    }

    private fun nickname(jwt: Jwt): String? = jwt.getClaimAsString("nickname")
        ?.trim()?.takeIf { it.isNotEmpty() }?.take(100)

    companion object {
        const val PROVIDER = "KAKAO"
    }
}
