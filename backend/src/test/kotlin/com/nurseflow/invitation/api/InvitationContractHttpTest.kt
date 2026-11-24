package com.nurseflow.invitation.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

/** Pins the invitation behaviour the OpenAPI contract and README describe. Fictional users only. */
class InvitationContractHttpTest : ApiTestBase() {
    private lateinit var wardId: UUID
    private lateinit var admin: String

    @BeforeEach
    fun setup() {
        wardId = insertWard("계약 병동")
        val (adminId, token) = userToken("가상 수간호사")
        grant(wardId, adminId, "WARD_ADMIN")
        admin = token
    }

    private fun issue(role: String): String =
        http.post("/api/v1/wards/$wardId/invitations", mapOf("role" to role), admin).json!!.get("token").asString()

    private fun redeem(token: String, body: Map<String, Any?>, user: String) =
        http.post("/api/v1/invitations/redeem", mapOf("token" to token) + body, user)

    @Test
    fun `invitations expire 24 hours after issue and take no custom lifetime`() {
        val created = http.post("/api/v1/wards/$wardId/invitations", mapOf("role" to "WARD_MEMBER"), admin).json!!.get("invitation")
        val lifetime = Duration.between(Instant.parse(created.get("createdAt").asString()), Instant.parse(created.get("expiresAt").asString()))

        assertEquals(Duration.ofHours(24), lifetime)
        assertEquals(400, http.post("/api/v1/wards/$wardId/invitations", mapOf("role" to "WARD_MEMBER", "expiresInHours" to 72), admin).status)
    }

    @Test
    fun `an admin invitation ignores a hospital joining month`() {
        val (_, user) = userToken("가상 관리자 후보")

        val joined = redeem(issue("WARD_ADMIN"), mapOf("hospitalJoinedOn" to "2020-03"), user)

        assertEquals(201, joined.status, joined.body)
        assertEquals("WARD_ADMIN", joined.json!!.get("roles")[0].asString())
    }

    @Test
    fun `a member invitation rejects a future or too old joining month`() {
        val (_, user) = userToken("가상 간호사")
        val nextMonth = YearMonth.now(ZoneId.of("Asia/Seoul")).plusMonths(1).toString()

        assertEquals("INVALID_HOSPITAL_JOIN_MONTH", redeem(issue("WARD_MEMBER"), mapOf("hospitalJoinedOn" to nextMonth), user).code)
        assertEquals("INVALID_HOSPITAL_JOIN_MONTH", redeem(issue("WARD_MEMBER"), mapOf("hospitalJoinedOn" to "1949-12"), user).code)
        assertEquals(400, redeem(issue("WARD_MEMBER"), mapOf("hospitalJoinedOn" to nextMonth), user).status)
    }

    @Test
    fun `a token whose user record is gone cannot create a nurse record`() {
        // A valid access token for a user id that has no users row (for example deleted after sign-in).
        val orphan = sessionTokens.issueAccessToken(UUID.randomUUID(), UUID.randomUUID()).value

        val response = redeem(issue("WARD_MEMBER"), mapOf("hospitalJoinedOn" to "2020-03"), orphan)

        assertEquals(401, response.status, response.body)
        assertEquals("USER_NOT_FOUND", response.code)
    }
}
