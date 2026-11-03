package com.nurseflow.invitation.persistence.repository

import com.nurseflow.invitation.persistence.entity.DbInvitableNurse
import com.nurseflow.invitation.persistence.entity.DbInvitation
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

@Repository
class InvitationJdbcRepository(private val jdbc: JdbcTemplate) {
    fun insert(
        id: UUID, wardId: UUID, role: String, nurseId: UUID?, tokenHash: String,
        createdBy: String, createdAt: Instant, expiresAt: Instant,
    ) {
        jdbc.update(
            """INSERT INTO ward_invitations(invitation_id,ward_id,role,nurse_id,token_hash,created_by,created_at,expires_at)
               VALUES(?,?,?,?,?,?,?,?)""",
            id, wardId, role, nurseId, tokenHash, createdBy, createdAt.toString(), expiresAt.toString(),
        )
    }

    fun listByWard(wardId: UUID): List<DbInvitation> = jdbc.query(
        "SELECT * FROM ward_invitations WHERE ward_id=? ORDER BY created_at DESC, invitation_id",
        { rs, _ -> map(rs) }, wardId,
    )

    fun lockInWard(wardId: UUID, invitationId: UUID): DbInvitation? = jdbc.query(
        "SELECT * FROM ward_invitations WHERE ward_id=? AND invitation_id=? FOR UPDATE",
        { rs, _ -> map(rs) }, wardId, invitationId,
    ).firstOrNull()

    fun revoke(invitationId: UUID, now: Instant) {
        jdbc.update("UPDATE ward_invitations SET revoked_at=? WHERE invitation_id=?", now.toString(), invitationId)
    }

    /** Unused, unrevoked invitations already pointing at this nurse record. */
    fun openInvitationsForNurse(nurseId: UUID): List<DbInvitation> = jdbc.query(
        "SELECT * FROM ward_invitations WHERE nurse_id=? AND redeemed_at IS NULL AND revoked_at IS NULL",
        { rs, _ -> map(rs) }, nurseId,
    )

    fun findNurseInWard(wardId: UUID, nurseId: UUID): DbInvitableNurse? = jdbc.query(
        "SELECT nurse_id, active, subject FROM nurses WHERE ward_id=? AND nurse_id=?",
        { rs, _ ->
            DbInvitableNurse(rs.getObject("nurse_id", UUID::class.java), rs.getBoolean("active"), rs.getString("subject") != null)
        },
        wardId, nurseId,
    ).firstOrNull()

    // ── Redemption ──

    /** Row-locks the invitation so two users cannot redeem the same link concurrently. */
    fun lockByTokenHash(tokenHash: String): DbInvitation? = jdbc.query(
        "SELECT * FROM ward_invitations WHERE token_hash=? FOR UPDATE",
        { rs, _ -> map(rs) }, tokenHash,
    ).firstOrNull()

    fun findByTokenHash(tokenHash: String): DbInvitation? = jdbc.query(
        "SELECT * FROM ward_invitations WHERE token_hash=?",
        { rs, _ -> map(rs) }, tokenHash,
    ).firstOrNull()

    fun markRedeemed(invitationId: UUID, subject: String, now: Instant): Boolean = jdbc.update(
        "UPDATE ward_invitations SET redeemed_at=?, redeemed_by=? WHERE invitation_id=? AND redeemed_at IS NULL",
        now.toString(), subject, invitationId,
    ) == 1

    /** Links an unclaimed, active nurse record to the user. Returns false if it was taken meanwhile. */
    fun linkNurse(wardId: UUID, nurseId: UUID, subject: String): Boolean = jdbc.update(
        "UPDATE nurses SET subject=? WHERE nurse_id=? AND ward_id=? AND subject IS NULL AND active=TRUE",
        subject, nurseId, wardId,
    ) == 1

    fun insertNurse(nurseId: UUID, wardId: UUID, subject: String, displayName: String) {
        jdbc.update(
            "INSERT INTO nurses(nurse_id,ward_id,subject,display_name,qualifications,active) VALUES(?,?,?,?,'[]',TRUE)",
            nurseId, wardId, subject, displayName,
        )
    }

    fun nurseLinkedTo(wardId: UUID, subject: String): Boolean = (jdbc.queryForObject(
        "SELECT COUNT(*) FROM nurses WHERE ward_id=? AND subject=?", Int::class.java, wardId, subject,
    ) ?: 0) > 0

    fun userDisplayName(subject: String): String? = runCatching { UUID.fromString(subject) }.getOrNull()?.let { id ->
        jdbc.query("SELECT display_name FROM users WHERE user_id=?", { rs, _ -> rs.getString(1) }, id).firstOrNull()
    }

    private fun map(rs: ResultSet) = DbInvitation(
        id = rs.getObject("invitation_id", UUID::class.java),
        wardId = rs.getObject("ward_id", UUID::class.java),
        role = rs.getString("role"),
        nurseId = rs.getObject("nurse_id", UUID::class.java),
        createdAt = Instant.parse(rs.getString("created_at")),
        expiresAt = Instant.parse(rs.getString("expires_at")),
        revokedAt = rs.getString("revoked_at")?.let(Instant::parse),
        redeemedAt = rs.getString("redeemed_at")?.let(Instant::parse),
    )
}
