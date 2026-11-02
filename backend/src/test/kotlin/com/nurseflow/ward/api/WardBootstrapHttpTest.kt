package com.nurseflow.ward.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WardBootstrapHttpTest : ApiTestBase() {
    @Test
    fun `bootstrap code makes the caller the first ward admin`() {
        val (_, token) = userToken("가상 수간호사")

        val created = http.post("/api/v1/wards",
            mapOf("name" to "가상 내과 병동", "timeZone" to "Asia/Seoul", "bootstrapCode" to "test-bootstrap-code"), token)

        assertEquals(201, created.status, created.body)
        val wardId = created.json!!.get("wardId").asString()
        assertEquals("WARD_ADMIN", created.json!!.get("roles").get(0).asString())
        val wards = http.get("/api/v1/wards", token).json!!.get("items")
        assertEquals(wardId, wards.get(0).get("wardId").asString())
        assertEquals(200, http.get("/api/v1/wards/$wardId/rosters?month=2026-12", token).status)
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit_events WHERE action='WARD_BOOTSTRAPPED'", Int::class.java))
    }

    @Test
    fun `wrong bootstrap code and invalid time zone are rejected`() {
        val (_, token) = userToken()

        val wrong = http.post("/api/v1/wards",
            mapOf("name" to "병동", "timeZone" to "Asia/Seoul", "bootstrapCode" to "guess"), token)
        val zone = http.post("/api/v1/wards",
            mapOf("name" to "병동", "timeZone" to "Mars/Base", "bootstrapCode" to "test-bootstrap-code"), token)
        val blank = http.post("/api/v1/wards",
            mapOf("name" to " ", "bootstrapCode" to "test-bootstrap-code"), token)

        assertEquals(403, wrong.status)
        assertEquals("BOOTSTRAP_CODE_INVALID", wrong.code)
        assertEquals("INVALID_TIME_ZONE", zone.code)
        assertEquals(listOf("name"), blank.fieldNames)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wards", Int::class.java))
    }
}
