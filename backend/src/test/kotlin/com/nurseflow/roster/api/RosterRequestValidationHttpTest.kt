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
    fun `same duty on two teams produces one senior slot per team`() {
        val (rosterId, etag) = createRoster()
        val teams = listOf("가상 A팀", "가상 B팀").map { name ->
            val res = http.post("/api/v1/wards/$wardId/teams", mapOf("name" to name), admin)
            assertEquals(201, res.status, res.body)
            res.json!!.get("teamId").asString()
        }
        val shift = UUID.randomUUID()
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'E','가상 이브닝','EVENING','15:00:00','23:00:00',FALSE)""", shift, wardId)
        val requirements = teams.map {
            mapOf("date" to "2026-12-01", "shiftTypeId" to shift, "requiredCount" to 2,
                "requiredQualifications" to emptyList<String>(), "teamId" to it)
        }
        jdbc.update("UPDATE shift_types SET kind='OTHER' WHERE shift_type_id=?", shift)
        val unsupported = http.put("/api/v1/rosters/$rosterId/requirements", mapOf("items" to requirements), admin,
            mapOf("If-Match" to etag))
        assertEquals(400, unsupported.status, unsupported.body)
        assertEquals("INVALID_REQUIREMENTS", unsupported.code)
        jdbc.update("UPDATE shift_types SET kind='EVENING' WHERE shift_type_id=?", shift)
        val saved = http.put("/api/v1/rosters/$rosterId/requirements", mapOf("items" to requirements), admin,
            mapOf("If-Match" to etag))
        assertEquals(200, saved.status, saved.body)
        assertEquals(2, saved.json!!.get("staffing").get("seniorUnfilled").asInt())
        val view = http.get("/api/v1/rosters/$rosterId", admin)
        assertEquals(200, view.status, view.body)
        val byTeam = view.json!!.get("slots").toList().groupBy { it.get("teamId").asString() }
        assertEquals(teams.toSet(), byTeam.keys)
        assertTrue(byTeam.values.all { slots -> slots.size == 2 && slots.count { it.get("seniorRequired").asBoolean() } == 1 })
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
    fun `non-positive shortfall acknowledgement is rejected before confirmation`() {
        val (rosterId, etag) = createRoster()

        val response = http.post("/api/v1/rosters/$rosterId/confirm",
            mapOf("acknowledgements" to mapOf("hourShortfalls" to listOf(mapOf("nurseId" to UUID.randomUUID().toString(), "shortfallMinutes" to 0)))),
            admin, idem() + ("If-Match" to etag))

        assertEquals(400, response.status)
        assertEquals("INVALID_REQUEST", response.code)
        assertEquals(listOf("acknowledgements.hourShortfalls[0].shortfallMinutes"), response.fieldNames)
    }

    @Test
    fun `the replaced unfilled count field is rejected as unknown`() {
        val (rosterId, etag) = createRoster()

        val response = http.post("/api/v1/rosters/$rosterId/confirm", mapOf("acknowledgeUnfilledCount" to 1),
            admin, idem() + ("If-Match" to etag))

        assertEquals(400, response.status, response.body)
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
