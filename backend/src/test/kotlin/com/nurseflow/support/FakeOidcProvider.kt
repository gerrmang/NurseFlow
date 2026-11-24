package com.nurseflow.support

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Instant
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * In-process stand-in for Kakao's OIDC provider. Serves a JWKS over real HTTP so the
 * production verifier fetches keys exactly as it would from kauth.kakao.com, and mints
 * ID tokens with fictional subjects. Nothing here is a real Kakao key or account.
 */
object FakeOidcProvider {
    const val ISSUER = "https://fake-kauth.nurseflow.test"
    const val ANDROID_APP_KEY = "nurseflow-test-android"
    const val WEB_APP_KEY = "nurseflow-test-web"
    const val REST_API_KEY = "nurseflow-test-rest"
    /** Matches `client-secret` in the test config; the token endpoint requires it like a secret-enabled Kakao app. */
    const val CLIENT_SECRET = "nurseflow-test-client-secret"
    const val REDIRECT_URI = "http://localhost:5173/auth/kakao/callback"
    const val WEB_ORIGIN = "http://localhost:5173"
    const val AUTHORIZE_URI = "https://fake-kauth.nurseflow.test/oauth/authorize"

    /** What Kakao remembers about an issued authorization code. */
    private data class PendingCode(
        val subject: String,
        val nonce: String?,
        val redirectUri: String,
        val audience: String,
        val nickname: String?,
    )

    private val codes = ConcurrentHashMap<String, PendingCode>()

    /** Number of calls the fake token endpoint received; lets tests prove a request never reached Kakao. */
    val tokenRequests = AtomicInteger()

    private val signingKey: RSAKey = RSAKeyGenerator(2048).keyID("fake-kakao-1").generate()
    private val strangerKey: RSAKey = RSAKeyGenerator(2048).keyID("fake-kakao-1").generate()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/.well-known/jwks.json") { exchange ->
            val body = JWKSet(signingKey.toPublicJWK()).toString().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        // Like kauth.kakao.com/oauth/token with Client Secret enabled: single-use codes bound to
        // client_id and redirect_uri, and requests without the matching client_secret are rejected.
        createContext("/oauth/token") { exchange ->
            tokenRequests.incrementAndGet()
            val form = exchange.requestBody.readAllBytes().decodeToString().split("&").filter { it.isNotEmpty() }.associate {
                val (k, v) = it.split("=", limit = 2)
                URLDecoder.decode(k, Charsets.UTF_8) to URLDecoder.decode(v, Charsets.UTF_8)
            }
            // Real Kakao answers a missing or wrong client secret with 401 invalid_client / KOE010.
            if (form["client_secret"] != CLIENT_SECRET) {
                val denied = """{"error":"invalid_client","error_description":"Bad client credentials","error_code":"KOE010"}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json;charset=utf-8")
                exchange.sendResponseHeaders(401, denied.size.toLong())
                exchange.responseBody.use { it.write(denied) }
                return@createContext
            }
            val pending = form["code"]?.let { codes.remove(it) }
            val ok = pending != null && form["grant_type"] == "authorization_code" &&
                form["client_id"] == REST_API_KEY && form["redirect_uri"] == pending.redirectUri
            val body = if (ok) {
                val token = idToken(pending!!.subject, audience = pending.audience, nonce = pending.nonce, nickname = pending.nickname)
                """{"token_type":"bearer","access_token":"fake-kakao-access","id_token":"$token","expires_in":21599}"""
            } else {
                """{"error":"invalid_grant","error_code":"KOE320"}"""
            }.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (ok) 200 else 400, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }

    val tokenUri: String get() = "http://127.0.0.1:${server.address.port}/oauth/token"

    /**
     * Simulates the user approving login on Kakao's page for the given authorize request:
     * returns a one-time code that the token endpoint will exchange for an ID token.
     */
    fun approve(
        subject: String,
        nonce: String?,
        redirectUri: String = REDIRECT_URI,
        audience: String = REST_API_KEY,
        nickname: String? = "가상 웹 사용자",
    ): String = "code-${UUID.randomUUID()}".also { codes[it] = PendingCode(subject, nonce, redirectUri, audience, nickname) }

    val jwkSetUri: String get() = "http://127.0.0.1:${server.address.port}/.well-known/jwks.json"

    fun idToken(
        subject: String,
        audience: String = ANDROID_APP_KEY,
        issuer: String = ISSUER,
        issuedAt: Instant = Instant.now(),
        expiresAt: Instant = Instant.now().plusSeconds(600),
        nonce: String? = null,
        nickname: String? = "가상 간호사",
        signedByStranger: Boolean = false,
    ): String {
        val claims = JWTClaimsSet.Builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .apply { nonce?.let { claim("nonce", it) } }
            .apply { nickname?.let { claim("nickname", it) } }
            .build()
        val key = if (signedByStranger) strangerKey else signingKey
        return SignedJWT(JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.keyID).build(), claims)
            .apply { sign(RSASSASigner(key)) }
            .serialize()
    }
}
