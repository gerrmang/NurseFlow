package com.nurseflow.invitation.api

import com.nurseflow.common.security.OpaqueToken
import com.nurseflow.support.ApiTestBase
import com.nurseflow.support.FakeOidcProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class InvitationRedemptionHttpTest : ApiTestBase() {
    private lateinit var wardId: UUID
    private lateinit var admin: String

    @BeforeEach
    fun setup() {
        wardId = insertWard("가상 외과 병동")
        val (adminId, token) = userToken("가상 수간호사")
        grant(wardId, adminId, "WARD_ADMIN")
        admin = token
    }

    private fun issue(role: String, nurseId: UUID? = null, ward: UUID = wardId, by: String = admin): String {
        val response = http.post("/api/v1/wards/$ward/invitations",
            buildMap { put("role", role); nurseId?.let { put("nurseId", it) } }, by)
        assertEquals(201, response.status, response.body)
        return response.json!!.get("token").asString()
    }

    /** Signs in through the fake Kakao provider, like a real client would. */
    private fun kakaoLogin(nickname: String): Pair<UUID, String> {
        val session = http.post("/api/v1/auth/kakao/session",
            mapOf("idToken" to FakeOidcProvider.idToken("fake-${UUID.randomUUID()}", nickname = nickname))).json!!
        return UUID.fromString(session.get("user").get("userId").asString()) to session.get("accessToken").asString()
    }

    private fun redeem(token: String, user: String) = http.post(
        "/api/v1/invitations/redeem", mapOf("token" to token, "hospitalJoinedOn" to "2020-03"), user,
    )

    private fun invitationStatus(): List<String> =
        http.get("/api/v1/wards/$wardId/invitations", admin).json!!.get("items").values().map { it.get("status").asString() }

    @Test
    fun `nurse joins through an invitation and gets member access only`() {
        val token = issue("WARD_MEMBER")
        val (userId, nurse) = kakaoLogin("가상 간호사 B")
        assertEquals(0, http.get("/api/v1/me", nurse).json!!.get("memberships").size())

        val preview = http.post("/api/v1/invitations/preview", mapOf("token" to token), nurse)
        assertEquals(200, preview.status, preview.body)
        assertEquals("가상 외과 병동", preview.json!!.get("wardName").asString())
        assertEquals("WARD_MEMBER", preview.json!!.get("role").asString())

        val joined = redeem(token, nurse)
        assertEquals(201, joined.status, joined.body)
        assertEquals(listOf("WARD_MEMBER"), joined.json!!.get("roles").values().map { it.asString() })

        val membership = http.get("/api/v1/me", nurse).json!!.get("memberships").get(0)
        assertEquals(wardId.toString(), membership.get("wardId").asString())
        assertEquals("가상 간호사 B", jdbc.queryForObject(
            "SELECT display_name FROM nurses WHERE ward_id=? AND subject=?", String::class.java, wardId, userId.toString()))
        assertEquals(listOf("REDEEMED"), invitationStatus())
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE action='INVITATION_REDEEMED'", Int::class.java))

        // Member: published calendars are reachable (no roster yet), admin features are not.
        assertEquals("PUBLISHED_ROSTER_NOT_FOUND",
            http.get("/api/v1/wards/$wardId/published-rosters/mine?month=2026-12", nurse).code)
        assertEquals("PUBLISHED_ROSTER_NOT_FOUND",
            http.get("/api/v1/wards/$wardId/published-rosters?month=2026-12", nurse).code)
        assertEquals("WARD_ADMIN_REQUIRED", http.get("/api/v1/wards/$wardId/rosters?month=2026-12", nurse).code)
        assertEquals("WARD_ADMIN_REQUIRED", http.post("/api/v1/wards/$wardId/rosters",
            mapOf("month" to "2026-12"), nurse, idem()).code)
        assertEquals("WARD_ADMIN_REQUIRED", http.post("/api/v1/wards/$wardId/invitations",
            mapOf("role" to "WARD_ADMIN"), nurse).code)
    }

    @Test
    fun `member must provide hospital joining month but admin does not`() {
        val memberInvite = issue("WARD_MEMBER")
        val (_, nurse) = kakaoLogin("가상 간호사")
        val missingMonth = http.post("/api/v1/invitations/redeem", mapOf("token" to memberInvite), nurse)

        assertEquals(400, missingMonth.status)
        assertEquals("HOSPITAL_JOIN_MONTH_REQUIRED", missingMonth.code)
        assertEquals(listOf("PENDING"), invitationStatus())
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ward_memberships WHERE subject IS NOT NULL AND role='WARD_MEMBER'", Int::class.java))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM nurses WHERE ward_id=?", Int::class.java, wardId))
        assertEquals(201, redeem(memberInvite, nurse).status)

        val adminInvite = issue("WARD_ADMIN")
        val (_, secondAdmin) = kakaoLogin("가상 부관리자")
        assertEquals(201, http.post("/api/v1/invitations/redeem", mapOf("token" to adminInvite), secondAdmin).status)
    }

    @Test
    fun `member invitation can take over an existing fictional nurse record`() {
        val nurseId = insertNurse(wardId, "가상 간호사 C")
        val token = issue("WARD_MEMBER", nurseId)
        val (userId, nurse) = kakaoLogin("카카오 닉네임")

        assertTrue(http.post("/api/v1/invitations/preview", mapOf("token" to token), nurse)
            .json!!.get("linksExistingNurse").asBoolean())
        assertEquals(201, redeem(token, nurse).status)

        assertEquals(userId.toString(), jdbc.queryForObject(
            "SELECT subject FROM nurses WHERE nurse_id=?", String::class.java, nurseId))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM nurses WHERE ward_id=?", Int::class.java, wardId))
    }

    @Test
    fun `admin invitation grants admin role without a nurse record`() {
        val token = issue("WARD_ADMIN")
        val (_, second) = kakaoLogin("가상 부관리자")

        assertEquals(201, http.post("/api/v1/invitations/redeem", mapOf("token" to token), second).status)

        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM nurses WHERE ward_id=?", Int::class.java, wardId))
        assertEquals(200, http.get("/api/v1/wards/$wardId/rosters?month=2026-12", second).status)
        assertEquals(201, http.post("/api/v1/wards/$wardId/invitations", mapOf("role" to "WARD_MEMBER"), second).status)
    }

    @Test
    fun `admin invitation is rejected while the user has an active nurse record`() {
        val (userId, nurse) = kakaoLogin("가상 간호사 겸 관리자")
        insertNurse(wardId, "가상 간호사", subject = userId.toString())
        val token = issue("WARD_ADMIN")

        val response = http.post("/api/v1/invitations/redeem", mapOf("token" to token), nurse)

        assertEquals(409, response.status)
        assertEquals("ADMIN_NURSE_RECORD_CONFLICT", response.code)
        assertEquals(0, http.get("/api/v1/me", nurse).json!!.get("memberships").size())
        assertEquals(listOf("PENDING"), invitationStatus())
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM nurses WHERE ward_id=? AND subject=? AND active=TRUE",
            Int::class.java, wardId, userId.toString()))
    }

    @Test
    fun `admin invitation can be accepted when the user's existing nurse record is inactive`() {
        val (userId, adminInvitee) = kakaoLogin("가상 부관리자")
        insertNurse(wardId, "비활성 간호사", subject = userId.toString(), active = false)
        val token = issue("WARD_ADMIN")

        assertEquals(201, http.post("/api/v1/invitations/redeem", mapOf("token" to token), adminInvitee).status)
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM ward_memberships WHERE subject=? AND role='WARD_ADMIN'",
            Int::class.java, userId.toString()))
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM nurses WHERE ward_id=? AND subject=? AND active=TRUE",
            Int::class.java, wardId, userId.toString()))
    }

    @Test
    fun `invitation is single use`() {
        val token = issue("WARD_MEMBER")
        val (_, first) = kakaoLogin("첫 사용자")
        val (_, second) = kakaoLogin("두 번째 사용자")

        assertEquals(201, redeem(token, first).status)
        val reuse = redeem(token, second)

        assertEquals(409, reuse.status)
        assertEquals("INVITATION_ALREADY_REDEEMED", reuse.code)
        assertEquals("INVITATION_ALREADY_REDEEMED", http.post("/api/v1/invitations/preview", mapOf("token" to token), second).code)
        assertEquals(0, http.get("/api/v1/me", second).json!!.get("memberships").size())
    }

    @Test
    fun `revoked expired unknown and malformed tokens are refused`() {
        val revoked = issue("WARD_MEMBER")
        val revokedId = http.get("/api/v1/wards/$wardId/invitations", admin).json!!.get("items").get(0).get("invitationId").asString()
        http.delete("/api/v1/wards/$wardId/invitations/$revokedId", admin)
        val expired = issue("WARD_MEMBER")
        jdbc.update("UPDATE ward_invitations SET expires_at=? WHERE token_hash=?",
            Instant.now().minusSeconds(1).toString(), OpaqueToken.sha256Hex(expired))
        val (_, user) = kakaoLogin("가상 사용자")

        assertEquals(410, redeem(revoked, user).status)
        assertEquals("INVITATION_REVOKED", redeem(revoked, user).code)
        assertEquals("INVITATION_EXPIRED", redeem(expired, user).code)
        assertEquals("INVITATION_NOT_FOUND", redeem(OpaqueToken.generate(), user).code)
        val malformed = redeem("not-a-token", user)
        assertEquals(400, malformed.status)
        assertEquals(listOf("token"), malformed.fieldNames)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ward_memberships", Int::class.java), "only the admin")
    }

    @Test
    fun `users cannot choose or raise their role`() {
        val token = issue("WARD_MEMBER")
        val (_, nurse) = kakaoLogin("가상 간호사")

        val escalation = http.post("/api/v1/invitations/redeem", mapOf("token" to token, "role" to "WARD_ADMIN"), nurse)

        assertEquals(400, escalation.status)
        assertEquals("INVALID_REQUEST", escalation.code)
        assertEquals(listOf("PENDING"), invitationStatus())
        assertEquals(listOf("WARD_MEMBER"), redeem(token, nurse).json!!.get("roles").values().map { it.asString() })
    }

    @Test
    fun `existing members cannot redeem again and the invitation stays usable`() {
        val token = issue("WARD_ADMIN")

        val again = http.post("/api/v1/invitations/redeem", mapOf("token" to token), admin)

        assertEquals(409, again.status)
        assertEquals("ALREADY_WARD_MEMBER", again.code)
        assertEquals(listOf("PENDING"), invitationStatus())
    }

    @Test
    fun `nurse record claimed after issuing rolls back the whole redemption`() {
        val nurseId = insertNurse(wardId)
        val token = issue("WARD_MEMBER", nurseId)
        jdbc.update("UPDATE nurses SET subject=? WHERE nurse_id=?", "someone-else", nurseId)
        val (userId, nurse) = kakaoLogin("가상 간호사")

        val response = redeem(token, nurse)

        assertEquals("NURSE_ALREADY_LINKED", response.code)
        assertEquals(listOf("PENDING"), invitationStatus())
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ward_memberships WHERE subject=?",
            Int::class.java, userId.toString()))
    }

    @Test
    fun `an invitation grants access to its own ward only`() {
        val otherWard = insertWard("다른 병동")
        val token = issue("WARD_ADMIN")
        val (_, user) = kakaoLogin("가상 관리자")

        redeem(token, user)

        assertEquals("WARD_NOT_FOUND", http.get("/api/v1/wards/$otherWard/rosters?month=2026-12", user).code)
        assertEquals("WARD_NOT_FOUND", http.post("/api/v1/wards/$otherWard/invitations", mapOf("role" to "WARD_ADMIN"), user).code)
    }

    @Test
    fun `concurrent redemptions of one link create exactly one membership`() {
        val token = issue("WARD_MEMBER")
        val users = (1..4).map { kakaoLogin("동시 사용자 $it").second }
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(users.size)

        val futures = users.map { user -> pool.submit<Int> { start.await(); redeem(token, user).status } }
        start.countDown()
        val statuses = futures.map { it.get(30, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(1, statuses.count { it == 201 }, statuses.toString())
        assertTrue(statuses.filter { it != 201 }.all { it == 409 }, statuses.toString())
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ward_memberships WHERE role='WARD_MEMBER'", Int::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM nurses WHERE ward_id=?", Int::class.java, wardId))
    }

    @Test
    fun `redemption requires a signed-in user`() {
        val token = issue("WARD_MEMBER")

        assertEquals(401, http.post("/api/v1/invitations/redeem", mapOf("token" to token)).status)
        assertEquals(401, http.post("/api/v1/invitations/preview", mapOf("token" to token)).status)
    }
}
