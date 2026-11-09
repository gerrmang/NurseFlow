package com.nurseflow.auth.persistence.repository

import com.nurseflow.auth.persistence.entity.DbRefreshToken
import com.nurseflow.auth.persistence.entity.DbUser
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class AuthJdbcRepository(private val jdbc: JdbcTemplate) {
    // ── Users ──

    fun findUserByProviderSubject(provider: String, providerSubject: String): DbUser? = jdbc.query(
        "SELECT user_id, display_name FROM users WHERE provider=? AND provider_subject=?",
        { rs, _ -> DbUser(rs.getObject("user_id", UUID::class.java), rs.getString("display_name")) },
        provider, providerSubject,
    ).firstOrNull()

    fun findUser(userId: UUID): DbUser? = jdbc.query(
        "SELECT user_id, display_name FROM users WHERE user_id=?",
        { rs, _ -> DbUser(rs.getObject("user_id", UUID::class.java), rs.getString("display_name")) },
        userId,
    ).firstOrNull()

    /** Throws DuplicateKeyException when a concurrent first login already created the user. */
    fun insertUser(userId: UUID, provider: String, providerSubject: String, displayName: String, now: Instant) {
        jdbc.update(
            """INSERT INTO users(user_id,provider,provider_subject,display_name,created_at,last_login_at)
               VALUES(?,?,?,?,?,?)""",
            userId, provider, providerSubject, displayName, now.toString(), now.toString(),
        )
    }

    fun touchUser(userId: UUID, displayName: String?, now: Instant) {
        if (displayName == null) {
            jdbc.update("UPDATE users SET last_login_at=? WHERE user_id=?", now.toString(), userId)
        } else {
            jdbc.update("UPDATE users SET last_login_at=?, display_name=? WHERE user_id=?", now.toString(), displayName, userId)
        }
    }

    // ── Refresh tokens (only SHA-256 hashes are stored) ──

    fun insertRefreshToken(id: UUID, familyId: UUID, userId: UUID, tokenHash: String, now: Instant, expiresAt: Instant) {
        jdbc.update(
            """INSERT INTO auth_refresh_tokens(token_id,family_id,user_id,token_hash,created_at,expires_at)
               VALUES(?,?,?,?,?,?)""",
            id, familyId, userId, tokenHash, now.toString(), expiresAt.toString(),
        )
    }

    /** Row-locks the token so concurrent refreshes of the same value cannot both rotate it. */
    fun lockRefreshTokenByHash(tokenHash: String): DbRefreshToken? = jdbc.query(
        "SELECT * FROM auth_refresh_tokens WHERE token_hash=? FOR UPDATE",
        { rs, _ -> mapToken(rs) }, tokenHash,
    ).firstOrNull()

    fun markReplaced(tokenId: UUID, now: Instant) {
        jdbc.update("UPDATE auth_refresh_tokens SET replaced_at=? WHERE token_id=?", now.toString(), tokenId)
    }

    fun revokeFamily(familyId: UUID, now: Instant) {
        jdbc.update(
            "UPDATE auth_refresh_tokens SET revoked_at=? WHERE family_id=? AND revoked_at IS NULL",
            now.toString(), familyId,
        )
    }

    private fun mapToken(rs: ResultSet) = DbRefreshToken(
        id = rs.getObject("token_id", UUID::class.java),
        familyId = rs.getObject("family_id", UUID::class.java),
        userId = rs.getObject("user_id", UUID::class.java),
        expiresAt = Instant.parse(rs.getString("expires_at")),
        replacedAt = rs.getString("replaced_at")?.let(Instant::parse),
        revokedAt = rs.getString("revoked_at")?.let(Instant::parse),
    )
}
