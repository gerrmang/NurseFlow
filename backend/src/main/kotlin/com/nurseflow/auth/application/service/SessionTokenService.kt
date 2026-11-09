package com.nurseflow.auth.application.service

import com.nurseflow.auth.application.model.IssuedAccessToken
import com.nurseflow.auth.config.AuthProperties
import com.nurseflow.auth.config.SessionTokenClaims
import com.nurseflow.common.security.OpaqueToken
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

@Component
class SessionTokenService(
    private val encoder: JwtEncoder,
    private val properties: AuthProperties,
) {
    /** Short-lived API token. `sub` is the NurseFlow user id used by ward memberships. */
    fun issueAccessToken(userId: UUID, sessionId: UUID, now: Instant = Instant.now()): IssuedAccessToken {
        val session = properties.session
        val expiresAt = now.plus(session.accessTokenTtl)
        val claims = JwtClaimsSet.builder()
            .issuer(session.issuer)
            .audience(listOf(session.audience))
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString())
            .claim(SessionTokenClaims.TOKEN_USE, SessionTokenClaims.ACCESS)
            .claim(SessionTokenClaims.SESSION_ID, sessionId.toString())
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build()
        return IssuedAccessToken(encoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue, expiresAt)
    }

    /** 256-bit opaque refresh token. Only its hash is persisted. */
    fun newOpaqueToken(): String = OpaqueToken.generate()

    fun hash(token: String): String = OpaqueToken.sha256Hex(token)
}
