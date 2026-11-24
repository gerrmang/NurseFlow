package com.nurseflow.auth.persistence.repository

import com.nurseflow.auth.persistence.entity.DbWebLoginAttempt
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/** Web login attempts. Stores only hashes of state and the browser-binding cookie. */
@Repository
class WebLoginAttemptJdbcRepository(private val jdbc: JdbcTemplate) {
    fun insert(
        id: UUID,
        stateHash: String,
        browserBindingHash: String,
        nonce: String,
        redirectUri: String,
        now: Instant,
        expiresAt: Instant,
    ) {
        jdbc.update(
            """INSERT INTO auth_web_login_attempts
               (attempt_id,state_hash,browser_binding_hash,nonce,redirect_uri,created_at,expires_at)
               VALUES(?,?,?,?,?,?,?)""",
            id, stateHash, browserBindingHash, nonce, redirectUri, now.toString(), expiresAt.toString(),
        )
    }

    /** Row-locks the attempt so two callbacks with the same state cannot both consume it. */
    fun lockByStateHash(stateHash: String): DbWebLoginAttempt? = jdbc.query(
        "SELECT * FROM auth_web_login_attempts WHERE state_hash=? FOR UPDATE",
        { rs, _ ->
            DbWebLoginAttempt(
                id = rs.getObject("attempt_id", UUID::class.java),
                browserBindingHash = rs.getString("browser_binding_hash"),
                nonce = rs.getString("nonce"),
                redirectUri = rs.getString("redirect_uri"),
                expiresAt = Instant.parse(rs.getString("expires_at")),
                consumedAt = rs.getString("consumed_at")?.let(Instant::parse),
            )
        },
        stateHash,
    ).firstOrNull()

    fun markConsumed(id: UUID, now: Instant) {
        jdbc.update("UPDATE auth_web_login_attempts SET consumed_at=? WHERE attempt_id=?", now.toString(), id)
    }

    /** Housekeeping. `cutoff` should be well past any expiry so ISO-8601 string order is safe. */
    fun deleteCreatedBefore(cutoff: Instant): Int =
        jdbc.update("DELETE FROM auth_web_login_attempts WHERE created_at < ?", cutoff.toString())
}
