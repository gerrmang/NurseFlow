package com.nurseflow.invitation.api

import com.nurseflow.common.security.OpaqueToken
import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.Duration
import java.util.UUID

class WardInvitationHttpTest : ApiTestBase() {
    private lateinit var wardId: UUID
    private lateinit var admin: String
    private lateinit var member: String

    @BeforeEach
    fun setup() {
        wardId = insertWard("초대 병동")
        val (adminId, adminToken) = userToken("가상 수간호사")
        val (memberId, memberToken) = userToken("가상 간호사")
        grant(wardId, adminId, "WARD_ADMIN")
        grant(wardId, memberId, "WARD_MEMBER")
        admin = adminToken
        member = memberToken
    }

    private fun invite(body: Map<String, Any?>, token: String = admin, ward: UUID = wardId) =
        http.post("/api/v1/wards/$ward/invitations", body, token)

    @Test
    fun `admin issues a single-use link and only its hash is stored`() {
        val response = invite(mapOf("role" to "WARD_MEMBER"))

        assertEquals(201, response.status, response.body)
        assertEquals("no-store", response.header("Cache-Control"))
        val body = response.json!!
        val token = body.get("token").asString()
        assertTrue(token.matches(Regex(OpaqueToken.PATTERN)))
        assertEquals("https://nurseflow.test/invite#token=$token", body.get("inviteUrl").asString())
        assertEquals("PENDING", body.get("invitation").get("status").asString())
        assertEquals(Duration.ofHours(24), Duration.between(
            Instant.parse(body.get("invitation").get("createdAt").asString()),
            Instant.parse(body.get("invitation").get("expiresAt").asString()),
        ))
        val stored = jdbc.queryForObject("SELECT token_hash FROM ward_invitations", String::class.java)!!
        assertEquals(OpaqueToken.sha256Hex(token), stored)
        assertFalse(stored.contains(token))
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE action='INVITATION_CREATED'", Int::class.java))
    }

    @Test
    fun `only admins of the ward manage invitations`() {
        val (_, outsider) = userToken("다른 사용자")
        val otherWard = insertWard("다른 병동")
        val (otherAdminId, otherAdmin) = userToken("다른 병동 관리자")
        grant(otherWard, otherAdminId, "WARD_ADMIN")
        val created = invite(mapOf("role" to "WARD_MEMBER")).json!!.get("invitation").get("invitationId").asString()

        assertEquals("WARD_ADMIN_REQUIRED", invite(mapOf("role" to "WARD_MEMBER"), member).code)
        assertEquals("WARD_ADMIN_REQUIRED", http.get("/api/v1/wards/$wardId/invitations", member).code)
        assertEquals("WARD_ADMIN_REQUIRED", http.delete("/api/v1/wards/$wardId/invitations/$created", member).code)
        assertEquals("WARD_NOT_FOUND", invite(mapOf("role" to "WARD_MEMBER"), outsider).code)
        assertEquals("WARD_NOT_FOUND", invite(mapOf("role" to "WARD_ADMIN"), otherAdmin).code)
        // Another ward's admin cannot reach this invitation through their own ward either.
        assertEquals("INVITATION_NOT_FOUND",
            http.delete("/api/v1/wards/$otherWard/invitations/$created", otherAdmin).code)
    }

    @Test
    fun `invitation targets are validated`() {
        val nurse = insertNurse(wardId)
        val linked = insertNurse(wardId, subject = UUID.randomUUID().toString())
        val inactive = insertNurse(wardId, active = false)
        val foreign = insertNurse(insertWard("다른 병동"))

        assertEquals("INVALID_INVITATION", invite(mapOf("role" to "WARD_ADMIN", "nurseId" to nurse)).code)
        assertEquals("NURSE_ALREADY_LINKED", invite(mapOf("role" to "WARD_MEMBER", "nurseId" to linked)).code)
        assertEquals("NURSE_NOT_FOUND", invite(mapOf("role" to "WARD_MEMBER", "nurseId" to inactive)).code)
        assertEquals("NURSE_NOT_FOUND", invite(mapOf("role" to "WARD_MEMBER", "nurseId" to foreign)).code)
        assertEquals(201, invite(mapOf("role" to "WARD_MEMBER", "nurseId" to nurse)).status)
        assertEquals("INVITATION_ALREADY_PENDING", invite(mapOf("role" to "WARD_MEMBER", "nurseId" to nurse)).code)
    }

    @Test
    fun `malformed invitation requests name the field`() {
        val role = invite(mapOf("role" to "SUPER_ADMIN"))
        val oldOverride = invite(mapOf("role" to "WARD_MEMBER", "expiresInHours" to 1))

        assertEquals(400, role.status)
        assertEquals(listOf("role"), role.fieldNames)
        assertEquals(400, oldOverride.status)
        assertEquals(listOf("expiresInHours"), oldOverride.fieldNames)
    }

    @Test
    fun `list reports status without tokens and revoke is idempotent`() {
        val pending = invite(mapOf("role" to "WARD_MEMBER")).json!!.get("invitation").get("invitationId").asString()
        val revoked = invite(mapOf("role" to "WARD_ADMIN")).json!!.get("invitation").get("invitationId").asString()
        val expired = invite(mapOf("role" to "WARD_MEMBER")).json!!.get("invitation").get("invitationId").asString()
        jdbc.update("UPDATE ward_invitations SET expires_at=? WHERE invitation_id=?",
            Instant.now().minusSeconds(1).toString(), UUID.fromString(expired))

        assertEquals(204, http.delete("/api/v1/wards/$wardId/invitations/$revoked", admin).status)
        assertEquals(204, http.delete("/api/v1/wards/$wardId/invitations/$revoked", admin).status)
        assertEquals("INVITATION_NOT_FOUND",
            http.delete("/api/v1/wards/$wardId/invitations/${UUID.randomUUID()}", admin).code)

        val list = http.get("/api/v1/wards/$wardId/invitations", admin)
        assertEquals(200, list.status)
        assertFalse(list.body.contains("token"))
        val statuses = list.json!!.get("items").values().associate {
            it.get("invitationId").asString() to it.get("status").asString()
        }
        assertEquals(mapOf(pending to "PENDING", revoked to "REVOKED", expired to "EXPIRED"), statuses)
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE action='INVITATION_REVOKED'", Int::class.java))
    }

    @Test
    fun `revoking frees the nurse for a new invitation`() {
        val nurse = insertNurse(wardId)
        val first = invite(mapOf("role" to "WARD_MEMBER", "nurseId" to nurse)).json!!.get("invitation").get("invitationId").asString()

        http.delete("/api/v1/wards/$wardId/invitations/$first", admin)

        assertEquals(201, invite(mapOf("role" to "WARD_MEMBER", "nurseId" to nurse)).status)
    }

    @Test
    fun `reissue revokes the old link and preserves its role and nurse target`() {
        val nurseId = insertNurse(wardId)
        val original = invite(mapOf("role" to "WARD_MEMBER", "nurseId" to nurseId)).json!!
        val originalInvitationId = original.get("invitation").get("invitationId").asString()
        val replacement = http.post(
            "/api/v1/wards/$wardId/invitations/$originalInvitationId/reissue", null, admin,
        )

        assertEquals(201, replacement.status, replacement.body)
        val retry = http.post("/api/v1/wards/$wardId/invitations/$originalInvitationId/reissue", null, admin)
        assertEquals(410, retry.status)
        assertEquals("INVITATION_REVOKED", retry.code)
        val replacementBody = replacement.json!!
        val newInvitation = replacementBody.get("invitation")
        assertEquals("WARD_MEMBER", newInvitation.get("role").asString())
        assertEquals(nurseId.toString(), newInvitation.get("nurseId").asString())
        assertEquals(Duration.ofHours(24), Duration.between(
            Instant.parse(newInvitation.get("createdAt").asString()),
            Instant.parse(newInvitation.get("expiresAt").asString()),
        ))
        assertEquals("REVOKED", http.get("/api/v1/wards/$wardId/invitations", admin).json!!
            .get("items").values().first { it.get("invitationId").asString() == originalInvitationId }
            .get("status").asString())

        val (_, newUser) = userToken("새 초대 사용자")
        assertEquals("INVITATION_REVOKED", http.post("/api/v1/invitations/redeem",
            mapOf("token" to original.get("token").asString()), newUser).code)
        assertEquals(201, http.post("/api/v1/invitations/redeem",
            mapOf("token" to replacementBody.get("token").asString()), newUser).status)
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE action='INVITATION_REISSUED' AND target_id=?",
            Int::class.java, originalInvitationId))
    }

    @Test
    fun `redeemed invitation cannot be reissued`() {
        val original = invite(mapOf("role" to "WARD_ADMIN")).json!!
        val inviteId = original.get("invitation").get("invitationId").asString()
        val (_, newUser) = userToken("새 관리자")
        assertEquals(201, http.post("/api/v1/invitations/redeem", mapOf("token" to original.get("token").asString()), newUser).status)

        assertEquals("INVITATION_ALREADY_REDEEMED", http.post(
            "/api/v1/wards/$wardId/invitations/$inviteId/reissue", null, admin,
        ).code)
    }

    @Test
    fun `reissuing an expired invitation revokes its source and prevents duplicate links`() {
        val original = invite(mapOf("role" to "WARD_ADMIN")).json!!
        val originalId = original.get("invitation").get("invitationId").asString()
        jdbc.update("UPDATE ward_invitations SET expires_at=? WHERE invitation_id=?",
            Instant.now().minusSeconds(1).toString(), UUID.fromString(originalId))

        val replacement = http.post("/api/v1/wards/$wardId/invitations/$originalId/reissue", null, admin)
        assertEquals(201, replacement.status, replacement.body)
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ward_invitations WHERE ward_id=?", Int::class.java, wardId))

        val retry = http.post("/api/v1/wards/$wardId/invitations/$originalId/reissue", null, admin)
        assertEquals(410, retry.status)
        assertEquals("INVITATION_REVOKED", retry.code)
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM ward_invitations WHERE ward_id=?", Int::class.java, wardId))

        val (_, newUser) = userToken("재발급 사용자")
        assertEquals(201, http.post("/api/v1/invitations/redeem",
            mapOf("token" to replacement.json!!.get("token").asString()), newUser).status)
    }
}
