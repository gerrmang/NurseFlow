package com.nurseflow.auth.config

import com.nurseflow.common.config.BootstrapProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.core.OAuth2Error
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtValidators
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * The API accepts only NurseFlow session tokens. Kakao ID tokens are verified once at login
 * (see KakaoIdTokenVerifier) and are never accepted as API bearer tokens.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties::class, BootstrapProperties::class)
class AuthConfiguration(private val properties: AuthProperties) {
    private val sessionKey: SecretKey by lazy {
        val bytes = properties.session.secret.toByteArray(Charsets.UTF_8)
        require(bytes.size >= 32) { "nurseflow.auth.session.secret(NURSEFLOW_SESSION_SECRET)는 32바이트 이상이어야 합니다." }
        SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun sessionJwtEncoder(): JwtEncoder = NimbusJwtEncoder.withSecretKey(sessionKey).build()

    /** Resource-server decoder for `Authorization: Bearer <NurseFlow access token>`. */
    @Bean
    fun sessionJwtDecoder(): JwtDecoder = NimbusJwtDecoder.withSecretKey(sessionKey)
        .macAlgorithm(MacAlgorithm.HS256)
        .build()
        .apply {
            setJwtValidator(DelegatingOAuth2TokenValidator(
                JwtValidators.createDefaultWithIssuer(properties.session.issuer),
                jwtCheck("audience") { properties.session.audience in it.audience.orEmpty() },
                jwtCheck("token_use") { it.getClaimAsString(SessionTokenClaims.TOKEN_USE) == SessionTokenClaims.ACCESS },
                jwtCheck("subject") { !it.subject.isNullOrBlank() },
            ))
        }
}

object SessionTokenClaims {
    const val TOKEN_USE = "token_use"
    const val ACCESS = "access"
    const val SESSION_ID = "sid"
}

/** Null-safe claim check: absent claims fail instead of reaching the predicate. */
internal fun jwtCheck(description: String, predicate: (Jwt) -> Boolean) = OAuth2TokenValidator<Jwt> { jwt ->
    if (predicate(jwt)) OAuth2TokenValidatorResult.success()
    else OAuth2TokenValidatorResult.failure(OAuth2Error("invalid_token", description, null))
}
