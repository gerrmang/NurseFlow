package com.nurseflow.roster.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID

class NightDedicatedHttpTest : ApiTestBase() {
    private val zone = ZoneId.of("Asia/Seoul")

    @Test
    fun `night policy defaults to 6 Nights 1 SOF and 2 dedicated for 3 months, admin only and ETag checked`() {
        val ward = insertWard()
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val path = "/api/v1/wards/$ward/night-policy"
        val initial = http.get(path, token)
        assertEquals(200, initial.status, initial.body)
        val json = initial.json!!
        assertEquals(6, json.get("sofNightThreshold").asInt())
        assertEquals(1, json.get("sofDays").asInt())
        assertEquals(2, json.get("dedicatedHeadcount").asInt())
        assertEquals(3, json.get("dedicatedRotationMonths").asInt())
        assertEquals(15, json.get("dedicatedMinNights").asInt())
        assertEquals("\"policy-0\"", initial.header("ETag"))
        val tag = mapOf("If-Match" to initial.header("ETag")!!)
        val body = mapOf(
            "sofNightThreshold" to 7, "sofDays" to 1, "dedicatedHeadcount" to 3, "dedicatedRotationMonths" to 2,
            "dedicatedMinNights" to 14,
        )
        assertEquals(403, http.get(path, memberToken).status)
        assertEquals(403, http.put(path, body, memberToken, tag).status)
        assertEquals("INVALID_NIGHT_POLICY", http.put(path, body - "sofDays", token, tag).code)
        assertEquals("INVALID_NIGHT_POLICY", http.put(path, body + ("sofNightThreshold" to 0), token, tag).code)
        assertEquals("INVALID_NIGHT_POLICY", http.put(path, body + ("dedicatedRotationMonths" to 13), token, tag).code)
        assertEquals("INVALID_NIGHT_POLICY", http.put(path, body + ("dedicatedMinNights" to 14.5), token, tag).code)
        assertEquals("INVALID_NIGHT_POLICY", http.put(path, body + ("dedicatedHeadcount" to "3"), token, tag).code)
        val saved = http.put(path, body, token, tag)
        assertEquals(200, saved.status, saved.body)
        assertEquals("\"policy-1\"", saved.header("ETag"))
        assertEquals(412, http.put(path, body + ("sofDays" to 2), token, tag).status)
        // Same values again change nothing.
        assertEquals(saved.header("ETag"), http.put(path, body, token, mapOf("If-Match" to saved.header("ETag")!!)).header("ETag"))
        assertEquals(7, http.get(path, token).json!!.get("sofNightThreshold").asInt())
    }

    @Test
    fun `dedicated terms default to the rotation, refuse overlaps and end early or cancel without losing history`() {
        val ward = insertWard()
        jdbc.update("UPDATE wards SET demo_mode=TRUE WHERE ward_id=?", ward)
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val nurse = insertNurse(ward, "가상 간호사 1")
        val inactive = insertNurse(ward, "가상 간호사 2", active = false)
        val other = insertWard()
        val stranger = insertNurse(other, "가상 간호사 3")
        val path = "/api/v1/wards/$ward/night-dedicated-terms"

        assertEquals(403, http.post(path, mapOf("nurseId" to nurse, "startMonth" to "2026-11"), memberToken).status)
        assertEquals(403, http.get(path, memberToken).status)
        val created = http.post(path, mapOf("nurseId" to nurse, "startMonth" to "2026-11"), token)
        assertEquals(201, created.status, created.body)
        val term = created.json!!
        assertEquals("2027-01", term.get("endMonth").asString())
        assertEquals("ACTIVE", term.get("status").asString())
        val termId = term.get("termId").asString()
        // A retry of the same term returns it; a different overlapping term is refused.
        val again = http.post(path, mapOf("nurseId" to nurse, "startMonth" to "2026-11", "endMonth" to "2027-01"), token)
        assertEquals(200, again.status, again.body)
        assertEquals(termId, again.json!!.get("termId").asString())
        assertEquals("NIGHT_DEDICATED_TERM_OVERLAP",
            http.post(path, mapOf("nurseId" to nurse, "startMonth" to "2027-01", "endMonth" to "2027-03"), token).code)
        assertEquals("INVALID_NIGHT_DEDICATED_TERM",
            http.post(path, mapOf("nurseId" to nurse, "startMonth" to "2027-05", "endMonth" to "2027-04"), token).code)
        assertEquals("INVALID_NIGHT_DEDICATED_TERM",
            http.post(path, mapOf("nurseId" to nurse, "startMonth" to "2027-02", "endMonth" to "2028-02"), token).code)
        assertEquals("INVALID_MONTH", http.post(path, mapOf("nurseId" to nurse, "startMonth" to "2027-13"), token).code)
        assertEquals("INVALID_NIGHT_DEDICATED_TERM", http.post(path, mapOf("nurseId" to inactive, "startMonth" to "2026-11"), token).code)
        assertEquals("NURSE_NOT_FOUND", http.post(path, mapOf("nurseId" to stranger, "startMonth" to "2026-11"), token).code)

        // Mid-rotation change: the term ends after November; extending is refused.
        val endPath = "$path/$termId/end"
        assertEquals(403, http.post(endPath, mapOf("lastMonth" to "2026-11"), memberToken).status)
        assertEquals("INVALID_NIGHT_DEDICATED_TERM", http.post(endPath, mapOf("lastMonth" to "2027-02"), token).code)
        val shortened = http.post(endPath, mapOf("lastMonth" to "2026-11"), token)
        assertEquals(200, shortened.status, shortened.body)
        assertEquals("SHORTENED", shortened.json!!.get("status").asString())
        assertEquals("2027-01", shortened.json!!.get("plannedEndMonth").asString())
        assertEquals(200, http.post(endPath, mapOf("lastMonth" to "2026-11"), token).status)
        // The freed months can now be recorded again, here as a new term from January.
        assertEquals(201, http.post(path, mapOf("nurseId" to nurse, "startMonth" to "2027-01"), token).status)
        // Ending before the start cancels; the record stays in the full list.
        assertEquals("INVALID_NIGHT_DEDICATED_TERM", http.post(endPath, mapOf("lastMonth" to "2026-09"), token).code)
        assertEquals("CANCELLED", http.post(endPath, mapOf("lastMonth" to "2026-10"), token).json!!.get("status").asString())
        assertEquals(1, http.get(path, token).json!!.get("items").size())
        assertEquals(2, http.get("$path?includeCancelled=true", token).json!!.get("items").size())
        assertEquals(404, http.post("$path/${UUID.randomUUID()}/end", mapOf("lastMonth" to "2026-11"), token).status)
        // Another ward's admin view does not reach this ward's term.
        assertEquals(404, http.post("/api/v1/wards/$other/night-dedicated-terms/$termId/end", mapOf("lastMonth" to "2026-11"), token).status)
    }

    @Test
    fun `diagnostics count Nights by start date and report SOF need, dedicated minimum and headcount`() {
        val ward = insertWard()
        jdbc.update("UPDATE wards SET demo_mode=TRUE WHERE ward_id=?", ward)
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val six = insertNurse(ward, "가상 간호사 1")
        val five = insertNurse(ward, "가상 간호사 2")
        val withSof = insertNurse(ward, "가상 간호사 3")
        val dedicated = insertNurse(ward, "가상 간호사 4")
        val patternOnly = insertNurse(ward, "가상 간호사 5")
        jdbc.update("UPDATE nurses SET work_pattern='NIGHT_ONLY' WHERE nurse_id IN (?,?)", dedicated, patternOnly)
        val night = shiftType(ward)
        val october = roster(ward, "2026-10", "PUBLISHED")
        val november = roster(ward, "2026-11", "DRAFT")
        // October 31 starts in October: it counts there, not in November, so `five` stays below the threshold.
        slot(october, night, LocalDate.of(2026, 10, 31), five)
        (1..5).forEach { slot(november, night, LocalDate.of(2026, 11, it * 2), six) }
        slot(november, night, LocalDate.of(2026, 11, 30), six)
        (1..5).forEach { slot(november, night, LocalDate.of(2026, 11, it * 2), five) }
        (1..6).forEach { slot(november, night, LocalDate.of(2026, 11, it * 2), withSof) }
        jdbc.update("INSERT INTO roster_rest_days(roster_id,nurse_id,rest_date,kind) VALUES(?,?,?,'SOF')", november, withSof, LocalDate.of(2026, 11, 20))
        (1..14).forEach { slot(november, night, LocalDate.of(2026, 11, it * 2), dedicated) }
        val termPath = "/api/v1/wards/$ward/night-dedicated-terms"
        assertEquals(201, http.post(termPath, mapOf("nurseId" to dedicated, "startMonth" to "2026-10"), token).status)

        val path = "/api/v1/rosters/$november/night-diagnostics"
        assertEquals(403, http.get(path, memberToken).status)
        val response = http.get(path, token)
        assertEquals(200, response.status, response.body)
        val json = response.json!!
        assertEquals("SHIFT_DATE", json.get("nightAttribution").asString())
        assertEquals(6, json.get("policy").get("sofNightThreshold").asInt())
        val items = json.get("items").associateBy { it.get("nurseId").asString() }
        assertEquals(5, items.size)
        items.getValue(six.toString()).let {
            assertEquals(6, it.get("nightCount").asInt())
            assertEquals(1, it.get("sofRequiredDays").asInt())
            assertEquals("MISSING", it.get("sofStatus").asString())
        }
        items.getValue(five.toString()).let {
            assertEquals(5, it.get("nightCount").asInt())
            assertEquals("NOT_REQUIRED", it.get("sofStatus").asString())
        }
        assertEquals("MET", items.getValue(withSof.toString()).get("sofStatus").asString())
        items.getValue(dedicated.toString()).let {
            assertEquals("DEDICATED_EXEMPT", it.get("sofStatus").asString())
            assertEquals("BELOW_MINIMUM", it.get("dedicatedNightStatus").asString())
            assertEquals(1, it.get("nightShortfall").asInt())
            assertEquals(3, it.get("dedicatedRunMonths").asInt())
            assertEquals(0, it.get("issues").size())
        }
        items.getValue(patternOnly.toString()).let {
            assertTrue(it.get("dedicatedTermId").isNull)
            assertEquals("NO_DEDICATED_TERM", it.get("issues")[0].asString())
        }
        val headcount = json.get("dedicatedHeadcount")
        assertEquals(2, headcount.get("required").asInt())
        assertEquals(1, headcount.get("assigned").asInt())
        assertEquals("SHORTAGE", headcount.get("status").asString())

        // The policy is read on every request: a minimum of 14 Nights is met, 7 Nights no longer need SOF.
        val policyPath = "/api/v1/wards/$ward/night-policy"
        val tag = mapOf("If-Match" to http.get(policyPath, token).header("ETag")!!)
        assertEquals(200, http.put(policyPath, mapOf(
            "sofNightThreshold" to 7, "sofDays" to 1, "dedicatedHeadcount" to 1, "dedicatedRotationMonths" to 3,
            "dedicatedMinNights" to 14,
        ), token, tag).status)
        val after = http.get(path, token).json!!
        val afterItems = after.get("items").associateBy { it.get("nurseId").asString() }
        assertEquals("NOT_REQUIRED", afterItems.getValue(six.toString()).get("sofStatus").asString())
        assertEquals("EXCESS", afterItems.getValue(withSof.toString()).get("sofStatus").asString())
        assertEquals("MET", afterItems.getValue(dedicated.toString()).get("dedicatedNightStatus").asString())
        assertEquals("MET", after.get("dedicatedHeadcount").get("status").asString())
        assertEquals(404, http.get("/api/v1/rosters/${UUID.randomUUID()}/night-diagnostics", token).status)
    }

    private fun shiftType(ward: UUID): UUID = UUID.randomUUID().also {
        jdbc.update(
            """INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
                VALUES(?,?,'N','N','NIGHT',?,?,TRUE)""",
            it, ward, LocalTime.of(23, 0), LocalTime.of(7, 0),
        )
    }

    private fun roster(ward: UUID, month: String, status: String): UUID = UUID.randomUUID().also {
        jdbc.update(
            "INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,?,?,1)",
            it, ward, month, status, status == "PUBLISHED",
        )
    }

    private fun slot(roster: UUID, type: UUID, date: LocalDate, nurse: UUID) {
        val startsAt = date.atTime(23, 0).atZone(zone).toInstant()
        jdbc.update(
            "INSERT INTO roster_slots(slot_id,roster_id,shift_date,shift_type_id,starts_at,ends_at,nurse_id) VALUES(?,?,?,?,?,?,?)",
            UUID.randomUUID(), roster, date, type, startsAt.toString(), startsAt.plusSeconds(8 * 3600).toString(), nurse,
        )
    }
}
