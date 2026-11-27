package com.nurseflow.roster.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import java.time.LocalDate
import java.util.UUID

class SetupReadinessHttpTest : ApiTestBase() {
    private fun item(json: JsonNode, key: String): JsonNode =
        json.get("items").first { it.get("key").asString() == key }

    private fun shiftType(ward: UUID, code: String, kind: String, start: String, end: String, spans: Boolean): UUID =
        UUID.randomUUID().also {
            jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
                VALUES(?,?,?,?,?,?,?,?)""", it, ward, code, code, kind, start, end, spans)
        }

    private fun team(ward: UUID, name: String, order: Int): UUID = UUID.randomUUID().also {
        jdbc.update("INSERT INTO ward_teams(team_id,ward_id,name,sort_order,active) VALUES(?,?,?,?,TRUE)", it, ward, name, order)
    }

    /** A rosterable member nurse in [team]. */
    private fun memberNurse(ward: UUID, team: UUID, senior: Boolean): UUID {
        val (user, _) = userToken()
        grant(ward, user, "WARD_MEMBER")
        return insertNurse(ward, subject = user.toString()).also {
            jdbc.update("UPDATE nurses SET team_id=?,senior_eligible=? WHERE nurse_id=?", team, senior, it)
        }
    }

    @Test
    fun `new ward lists each missing item and generation is refused until they are set`() {
        val ward = insertWard()
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        val (_, outsiderToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val path = "/api/v1/wards/$ward/setup-readiness"

        assertEquals(403, http.get(path, memberToken).status)
        assertEquals(404, http.get(path, outsiderToken).status)
        val fresh = http.get(path, token)
        assertEquals(200, fresh.status, fresh.body)
        assertEquals("private, no-store", fresh.header("Cache-Control"))
        val freshJson = fresh.json!!
        assertFalse(freshJson.get("ready").asBoolean())
        assertEquals("MISSING", item(freshJson, "SHIFT_TYPES").get("status").asString())
        assertTrue(item(freshJson, "SHIFT_TYPES").get("blocking").asBoolean())
        assertEquals("MISSING", item(freshJson, "STAFF").get("status").asString())
        assertEquals("DEFAULT", item(freshJson, "TARGET_HOURS").get("status").asString())
        assertFalse(item(freshJson, "TARGET_HOURS").get("blocking").asBoolean())

        val created = http.post("/api/v1/wards/$ward/rosters", mapOf("month" to "2027-01"), token, idem())
        assertEquals(201, created.status, created.body)
        val rosterId = created.json!!.get("rosterId").asString()
        val precheckPath = "/api/v1/rosters/$rosterId/generation-precheck"
        assertEquals(403, http.get(precheckPath, memberToken).status)
        val precheck = http.get(precheckPath, token).json!!
        assertFalse(precheck.get("ready").asBoolean())
        assertEquals("MISSING", item(precheck, "REQUIREMENTS").get("status").asString())

        val refused = http.post("/api/v1/rosters/$rosterId/generation-jobs", mapOf("preserveLockedAssignments" to true),
            token, idem() + ("If-Match" to created.header("ETag")!!))
        assertEquals(422, refused.status, refused.body)
        assertEquals("SETUP_INCOMPLETE", refused.code)
        assertEquals(
            listOf("SETUP_SHIFT_TYPES", "SETUP_STAFF", "SETUP_REQUIREMENTS"),
            refused.json!!.get("ruleViolations").toList().map { it.get("code").asString() },
        )
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM generation_jobs", Int::class.java))
    }

    @Test
    fun `senior shortage is a notice and does not block generation`() {
        val ward = insertWard()
        val (admin, token) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        shiftType(ward, "D", "DAY", "07:00:00", "15:30:00", false)
        shiftType(ward, "E", "EVENING", "15:00:00", "23:30:00", false)
        val night = shiftType(ward, "N", "NIGHT", "22:00:00", "07:30:00", true)
        val teamA = team(ward, "가상 A팀", 1)
        val teamB = team(ward, "가상 B팀", 2)
        memberNurse(ward, teamA, senior = true)
        memberNurse(ward, teamB, senior = true)
        memberNurse(ward, teamB, senior = false)

        val ready = http.get("/api/v1/wards/$ward/setup-readiness", token).json!!
        assertTrue(ready.get("ready").asBoolean(), ready.toString())
        assertEquals("COMPLETE", item(ready, "TEAM_SENIORS").get("status").asString())

        val created = http.post("/api/v1/wards/$ward/rosters", mapOf("month" to "2027-01"), token, idem())
        val rosterId = created.json!!.get("rosterId").asString()
        // Team B needs a senior on every night of January but has only one senior.
        val items = (1..31).map {
            mapOf("date" to LocalDate.of(2027, 1, it).toString(), "shiftTypeId" to night, "teamId" to teamB,
                "requiredCount" to 1, "requiredQualifications" to emptyList<String>())
        }
        val saved = http.put("/api/v1/rosters/$rosterId/requirements", mapOf("items" to items), token,
            mapOf("If-Match" to created.header("ETag")!!))
        assertEquals(200, saved.status, saved.body)

        val precheck = http.get("/api/v1/rosters/$rosterId/generation-precheck", token).json!!
        assertTrue(precheck.get("ready").asBoolean(), precheck.toString())
        assertEquals(26, precheck.get("maxShiftsPerNurse").asInt())
        val nightCoverage = precheck.get("seniorCoverage").first {
            it.get("teamId").asString() == teamB.toString() && it.get("shiftKind").asString() == "NIGHT"
        }
        assertEquals(31, nightCoverage.get("requiredSeniorSlots").asInt())
        assertEquals(1, nightCoverage.get("availableSeniors").asInt())
        assertTrue(nightCoverage.get("shortage").asBoolean())
        assertEquals("MONTHLY_CAPACITY", nightCoverage.get("reasons").get(0).asString())

        val started = http.post("/api/v1/rosters/$rosterId/generation-jobs", mapOf("preserveLockedAssignments" to true),
            token, idem() + ("If-Match" to saved.header("ETag")!!))
        assertEquals(202, started.status, started.body)
    }
}
