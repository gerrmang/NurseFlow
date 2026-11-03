package com.nurseflow.roster.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class RosterRequestValidationHttpTest : ApiTestBase() {
    private lateinit var admin: String
    private lateinit var wardId: UUID

    @BeforeEach
    fun setup() {
        val (adminId, token) = userToken("검증 관리자")
        admin = token
        wardId = insertWard("검증 병동")
        grant(wardId, adminId, "WARD_ADMIN")
    }

    private fun createRoster(): Pair<String, String> {
        val created = http.post("/api/v1/wards/$wardId/rosters", mapOf("month" to "2026-12"), admin, idem())
        assertEquals(201, created.status, created.body)
        return created.json!!.get("rosterId").asString() to created.header("ETag")!!
    }

    @Test
    fun `invalid month keeps INVALID_MONTH code and names the field`() {
        val response = http.post("/api/v1/wards/$wardId/rosters", mapOf("month" to "2026-13"), admin, idem())

        assertEquals(400, response.status)
        assertEquals("application/problem+json", response.header("Content-Type"))
        assertEquals("INVALID_MONTH", response.code)
        assertEquals(listOf("month"), response.fieldNames)
        assertFalse(response.body.contains("2026-13"), "rejected value must not be echoed")
    }

    @Test
    fun `missing required field and unknown field are INVALID_REQUEST`() {
        val missing = http.post("/api/v1/wards/$wardId/rosters", "{}", admin, idem())
        val unknown = http.post("/api/v1/wards/$wardId/rosters", """{"month":"2026-12","role":"WARD_ADMIN"}""", admin, idem())

        assertEquals(400, missing.status)
        assertEquals("INVALID_REQUEST", missing.code)
        assertEquals(400, unknown.status)
        assertEquals("INVALID_REQUEST", unknown.code)
    }

    @Test
    fun `nested requirement errors report indexed field paths`() {
        val (rosterId, etag) = createRoster()
        val body = mapOf("items" to listOf(mapOf(
            "date" to "2026-12-01",
            "shiftTypeId" to UUID.randomUUID().toString(),
            "requiredCount" to 101,
            "requiredQualifications" to listOf(""),
        )))

        val response = http.put("/api/v1/rosters/$rosterId/requirements", body, admin, mapOf("If-Match" to etag))

        assertEquals(400, response.status, response.body)
        assertEquals("INVALID_REQUIREMENTS", response.code)
        val fields = response.fieldNames
        assertTrue("items[0].requiredCount" in fields, fields.toString())
        assertTrue(fields.any { it.startsWith("items[0].requiredQualifications") }, fields.toString())
    }

    @Test
    fun `absence kind and generation limit keep their dedicated codes`() {
        val (rosterId, etag) = createRoster()
        val absence = http.post("/api/v1/rosters/$rosterId/absences", mapOf(
            "nurseId" to UUID.randomUUID().toString(),
            "kind" to "SICK_LEAVE",
            "startsAt" to "2026-12-01T00:00:00Z",
            "endsAt" to "2026-12-02T00:00:00Z",
        ), admin, idem() + ("If-Match" to etag))
        val generation = http.post("/api/v1/rosters/$rosterId/generation-jobs",
            mapOf("preserveLockedAssignments" to true, "timeLimitSeconds" to 0), admin, idem() + ("If-Match" to etag))

        assertEquals("INVALID_ABSENCE", absence.code)
        assertEquals(listOf("kind"), absence.fieldNames)
        assertFalse(absence.body.contains("SICK_LEAVE"))
        assertEquals("INVALID_TIME_LIMIT", generation.code)
    }

    @Test
    fun `negative unfilled acknowledgement is rejected before confirmation`() {
        val (rosterId, etag) = createRoster()

        val response = http.post("/api/v1/rosters/$rosterId/confirm", mapOf("acknowledgeUnfilledCount" to -1),
            admin, idem() + ("If-Match" to etag))

        assertEquals(400, response.status)
        assertEquals("INVALID_REQUEST", response.code)
        assertEquals(listOf("acknowledgeUnfilledCount"), response.fieldNames)
    }

    @Test
    fun `valid request is unaffected and business errors omit fieldErrors`() {
        val (rosterId, etag) = createRoster()

        val response = http.post("/api/v1/rosters/$rosterId/confirm", mapOf<String, Any>(), admin,
            idem() + ("If-Match" to etag))

        assertEquals(422, response.status, response.body)
        assertEquals("ROSTER_REQUIREMENTS_REQUIRED", response.code)
        assertFalse(response.json!!.has("fieldErrors"))
    }
}
