package com.nurseflow.roster.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class SupportAllowanceHttpTest : ApiTestBase() {
    @Test
    fun `admin grants lists and revokes support within the ward only`() {
        val ward = insertWard()
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        val (_, outsiderToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val home = team(ward, 1)
        val target = team(ward, 2)
        val nurse = insertNurse(ward, subject = member.toString())
        jdbc.update("UPDATE nurses SET team_id=? WHERE nurse_id=?", home, nurse)
        val otherWard = insertWard()
        val otherNurse = insertNurse(otherWard)
        val otherTeam = team(otherWard, 1)
        val shift = UUID.randomUUID()
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'DAY','데이','DAY','07:00:00','15:00:00',FALSE)""", shift, ward)
        val path = "/api/v1/wards/$ward/support-allowances"
        val body = mapOf("nurseId" to nurse, "teamId" to target, "startDate" to "2026-12-01", "endDate" to "2026-12-31",
            "shiftTypeIds" to listOf(shift))

        assertEquals(403, http.post(path, body, memberToken).status)
        assertEquals(403, http.get(path, memberToken).status)
        assertEquals(404, http.post(path, body, outsiderToken).status)
        val reversed = http.post(path, body + ("endDate" to "2026-11-30"), token)
        assertEquals("INVALID_SUPPORT_ALLOWANCE", reversed.code, "${reversed.status} ${reversed.body}")
        assertEquals("INVALID_SUPPORT_ALLOWANCE", http.post(path, body + ("endDate" to "2027-12-02"), token).code)
        assertEquals("INVALID_SUPPORT_ALLOWANCE", http.post(path, body + ("teamId" to home), token).code)
        assertEquals("INVALID_REQUEST", http.post(path, body - "teamId", token).code)
        assertEquals("INVALID_REQUEST", http.post(path, body + ("shiftTypeIds" to List(21) { UUID.randomUUID() }), token).code)
        assertEquals("TEAM_NOT_FOUND", http.post(path, body + ("teamId" to otherTeam), token).code)
        assertEquals("NURSE_NOT_FOUND", http.post(path, body + ("nurseId" to otherNurse), token).code)
        assertEquals("SHIFT_TYPE_NOT_FOUND", http.post(path, body + ("shiftTypeIds" to listOf(UUID.randomUUID())), token).code)

        val created = http.post(path, body, token)
        assertEquals(201, created.status, created.body)
        val id = created.json!!.get("allowanceId").asString()
        assertEquals("ACTIVE", created.json!!.get("status").asString())
        assertEquals(shift.toString(), created.json!!.get("shiftTypeIds").get(0).asString())
        val repeated = http.post(path, body, token)
        assertEquals(200, repeated.status, repeated.body)
        assertEquals(id, repeated.json!!.get("allowanceId").asString())
        val allShifts = http.post(path, body - "shiftTypeIds", token)
        assertEquals(201, allShifts.status, allShifts.body)
        assertEquals(0, allShifts.json!!.get("shiftTypeIds").size())
        assertEquals(2, http.get(path, token).json!!.get("items").size())

        assertEquals(403, http.post("$path/$id/revoke", null, memberToken).status)
        assertEquals("SUPPORT_ALLOWANCE_NOT_FOUND", http.post("$path/${UUID.randomUUID()}/revoke", null, token).code)
        // An allowance is reached only through its own ward, even by an admin of both wards.
        grant(otherWard, admin, "WARD_ADMIN")
        assertEquals("SUPPORT_ALLOWANCE_NOT_FOUND",
            http.post("/api/v1/wards/$otherWard/support-allowances/$id/revoke", null, token).code)
        val revoked = http.post("$path/$id/revoke", null, token)
        assertEquals(200, revoked.status, revoked.body)
        assertEquals("REVOKED", revoked.json!!.get("allowance").get("status").asString())
        assertEquals(0, revoked.json!!.get("flaggedDraftCount").asInt())
        assertEquals(200, http.post("$path/$id/revoke", null, token).status)
        assertEquals(1, http.get(path, token).json!!.get("items").size())
        assertEquals(2, http.get("$path?includeRevoked=true", token).json!!.get("items").size())
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE action='SUPPORT_ALLOWANCE_REVOKED' AND target_id=?", Int::class.java, id))
    }

    private fun team(ward: UUID, order: Int): UUID = UUID.randomUUID().also {
        jdbc.update("INSERT INTO ward_teams(team_id,ward_id,name,sort_order,active) VALUES(?,?,?,?,TRUE)", it, ward, "가상 팀 $order", order)
    }
}
