package com.nurseflow.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import java.time.Instant

/** Test-only decoder: a bearer value `sub.<id>` authenticates as `<id>`. */
@TestConfiguration(proxyBeanMethods = false)
class SubjectJwtDecoderConfig {
    @Bean
    @Primary
    fun subjectJwtDecoder(): JwtDecoder = JwtDecoder { token ->
        val subject = token.removePrefix("sub.").takeIf { token.startsWith("sub.") && it.isNotBlank() }
            ?: throw BadJwtException("invalid test token")
        Jwt.withTokenValue(token).header("alg", "none").subject(subject)
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build()
    }
}
