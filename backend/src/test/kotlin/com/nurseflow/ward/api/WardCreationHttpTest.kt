package com.nurseflow.ward.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WardCreationHttpTest : ApiTestBase() {
    @Test
    fun `signed-in user creates a ward and becomes its admin`() {
        val (_, token) = userToken("가상 수간호사")

        val created = http.post("/api/v1/wards", mapOf("name" to "가상 내과 병동", "timeZone" to "Asia/Seoul", "careerStartedOn" to "2012-03", "rank" to "팀장"), token)

        assertEquals(201, created.status, created.body)
        val wardId = created.json!!.get("wardId").asString()
        assertEquals("WARD_ADMIN", created.json!!.get("roles").get(0).asString())
        assertEquals(wardId, http.get("/api/v1/wards", token).json!!.get("items").get(0).get("wardId").asString())
        assertEquals(200, http.get("/api/v1/wards/$wardId/rosters?month=2026-12", token).status)
        assertEquals(201, http.post("/api/v1/wards/$wardId/invitations", mapOf("role" to "WARD_MEMBER"), token).status)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='WARD_CREATED'", Int::class.java))
        assertEquals("2012-03|팀장", jdbc.queryForObject(
            "SELECT career_started_on || '|' || rank_name FROM ward_memberships WHERE ward_id=?", String::class.java,
            java.util.UUID.fromString(wardId)))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM nurses", Int::class.java), "admins are not rostered nurses")
    }

    private fun ward(name: String) = mapOf("name" to name, "careerStartedOn" to "2020-01", "rank" to "과장")

    @Test
    fun `creating a ward grants nothing in other wards`() {
        val (_, other) = userToken("다른 관리자")
        val otherWard = http.post("/api/v1/wards", ward("다른 병동"), other).json!!.get("wardId").asString()
        val (_, token) = userToken("가상 수간호사")

        http.post("/api/v1/wards", ward("내 병동"), token)

        assertEquals("WARD_NOT_FOUND", http.get("/api/v1/wards/$otherWard/rosters?month=2026-12", token).code)
        assertEquals(1, http.get("/api/v1/wards", token).json!!.get("items").size())
    }

    @Test
    fun `invalid name, time zone and old code field are rejected`() {
        val (_, token) = userToken()

        assertEquals(listOf("name"), http.post("/api/v1/wards", ward(" "), token).fieldNames)
        assertEquals("INVALID_TIME_ZONE", http.post("/api/v1/wards", ward("병동") + ("timeZone" to "Mars/Base"), token).code)
        assertEquals("INVALID_REQUEST", http.post("/api/v1/wards", ward("병동") + ("bootstrapCode" to "x"), token).code)
        assertEquals("INVALID_REQUEST", http.post("/api/v1/wards", mapOf("name" to "병동"), token).code)
        assertEquals(listOf("careerStartedOn"), http.post("/api/v1/wards", ward("병동") + ("careerStartedOn" to "2020-13"), token).fieldNames)
        assertEquals("RANK_NOT_IN_POLICY", http.post("/api/v1/wards", ward("병동") + ("rank" to "원장"), token).code)
        assertEquals("INVALID_CAREER_START", http.post("/api/v1/wards", ward("병동") + ("careerStartedOn" to "2999-01"), token).code)
        assertEquals(401, http.post("/api/v1/wards", ward("병동")).status)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wards", Int::class.java))
    }
}
