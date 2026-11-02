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
import java.time.Instant
import java.util.Date

/**
 * In-process stand-in for Kakao's OIDC provider. Serves a JWKS over real HTTP so the
 * production verifier fetches keys exactly as it would from kauth.kakao.com, and mints
 * ID tokens with fictional subjects. Nothing here is a real Kakao key or account.
 */
object FakeOidcProvider {
    const val ISSUER = "https://fake-kauth.nurseflow.test"
    const val ANDROID_APP_KEY = "nurseflow-test-android"
    const val WEB_APP_KEY = "nurseflow-test-web"

    private val signingKey: RSAKey = RSAKeyGenerator(2048).keyID("fake-kakao-1").generate()
    private val strangerKey: RSAKey = RSAKeyGenerator(2048).keyID("fake-kakao-1").generate()

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/.well-known/jwks.json") { exchange ->
            val body = JWKSet(signingKey.toPublicJWK()).toString().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        start()
    }

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
