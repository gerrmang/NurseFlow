package com.nurseflow.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("nurseflow.auth")
data class AuthProperties(
    val kakao: Kakao = Kakao(),
    val session: Session = Session(),
) {
    /** Kakao OIDC ID token verification. `audiences` are the Kakao app keys used by the clients. */
    data class Kakao(
        val issuer: String = "https://kauth.kakao.com",
        val jwkSetUri: String = "https://kauth.kakao.com/.well-known/jwks.json",
        val audiences: List<String> = emptyList(),
        val maxIdTokenAge: Duration = Duration.ofMinutes(10),
    )

    /** NurseFlow-issued session: short access JWT plus rotating opaque refresh token. */
    data class Session(
        val secret: String = "",
        val issuer: String = "nurseflow",
        val audience: String = "nurseflow-api",
        val accessTokenTtl: Duration = Duration.ofMinutes(10),
        val refreshTokenTtl: Duration = Duration.ofDays(30),
    )
}
