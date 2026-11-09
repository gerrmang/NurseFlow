package com.nurseflow.auth.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("nurseflow.auth")
data class AuthProperties(
    val kakao: Kakao = Kakao(),
    val session: Session = Session(),
    val web: Web = Web(),
) {
    /** Kakao OIDC ID token verification. `audiences` are the Kakao app keys used by the clients. */
    data class Kakao(
        val issuer: String = "https://kauth.kakao.com",
        val jwkSetUri: String = "https://kauth.kakao.com/.well-known/jwks.json",
        val audiences: List<String> = emptyList(),
        val maxIdTokenAge: Duration = Duration.ofMinutes(10),
        /** Web login (authorization code flow): Kakao endpoints and the REST API key used as client_id. */
        val authorizeUri: String = "https://kauth.kakao.com/oauth/authorize",
        val tokenUri: String = "https://kauth.kakao.com/oauth/token",
        val restApiKey: String = "",
        /** Optional Kakao client secret. Sent only from the server, never to the browser. */
        val clientSecret: String = "",
        /** Exact web callback URIs. Each must also be registered in Kakao Developers. */
        val redirectUris: List<String> = emptyList(),
        /** Optional `scope` for the authorize request. Empty uses the consent items set in Kakao Developers. */
        val scope: String = "",
        val tokenExchangeTimeout: Duration = Duration.ofSeconds(5),
    ) {
        val webRedirectUris: List<String> get() = redirectUris.map { it.trim() }.filter { it.isNotEmpty() }

        val webLoginConfigured: Boolean get() = restApiKey.isNotBlank() && webRedirectUris.isNotEmpty()
    }

    /** NurseFlow-issued session: short access JWT plus rotating opaque refresh token. */
    data class Session(
        val secret: String = "",
        val issuer: String = "nurseflow",
        val audience: String = "nurseflow-api",
        val accessTokenTtl: Duration = Duration.ofMinutes(10),
        val refreshTokenTtl: Duration = Duration.ofDays(30),
    )

    /**
     * Browser session. The refresh token lives only in an HttpOnly cookie scoped to [COOKIE_PATH];
     * cookie-authenticated endpoints additionally require the custom client header and an allowed Origin.
     */
    data class Web(
        val loginAttemptTtl: Duration = Duration.ofMinutes(10),
        val refreshCookieName: String = "nf_refresh",
        val loginCookieName: String = "nf_kakao_login",
        /** Keep true. Browsers treat http://localhost as a secure context, so local development still works. */
        val cookieSecure: Boolean = true,
        /** Allowed `Origin` values. Empty means the origins of the Kakao redirect URIs. */
        val allowedOrigins: List<String> = emptyList(),
    ) {
        companion object {
            const val COOKIE_PATH = "/api/v1/auth/web"
        }
    }

    /** Origins that may call the cookie-authenticated web endpoints, in the form browsers send them. */
    val webAllowedOrigins: Set<String>
        get() = web.allowedOrigins.map { it.trim().trimEnd('/') }.filter { it.isNotEmpty() }
            .map { originOf(it) ?: it.lowercase() }.toSet()
            .ifEmpty { kakao.webRedirectUris.mapNotNull(::originOf).toSet() }

    /** Browsers omit the scheme's default port from `Origin`, so `:443`/`:80` must be dropped here too. */
    private fun originOf(uri: String): String? = runCatching {
        val parsed = URI(uri)
        val scheme = parsed.scheme?.lowercase() ?: return null
        val host = parsed.host?.lowercase() ?: return null
        val defaultPort = (scheme == "https" && parsed.port == 443) || (scheme == "http" && parsed.port == 80)
        if (parsed.port == -1 || defaultPort) "$scheme://$host" else "$scheme://$host:${parsed.port}"
    }.getOrNull()
}
