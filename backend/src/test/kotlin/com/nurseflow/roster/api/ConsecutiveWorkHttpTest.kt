package com.nurseflow.roster.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ConsecutiveWorkHttpTest : ApiTestBase() {
    @Test
    fun `ward policy checks role revision and range then invalidates only drafts in the ward`() {
        val ward = insertWard()
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val path = "/api/v1/wards/$ward/consecutive-work-policy"
        val initial = http.get(path, token)
        assertEquals(5, initial.json!!.get("maxConsecutiveWorkDays").asInt())
        assertEquals("\"policy-0\"", initial.header("ETag"))
        val tag = mapOf("If-Match" to initial.header("ETag")!!)
        assertEquals(403, http.get(path, memberToken).status)
        assertEquals(403, http.put(path, mapOf("maxConsecutiveWorkDays" to 6), memberToken, tag).status)
        assertEquals(400, http.put(path, mapOf("maxConsecutiveWorkDays" to 0), token, tag).status)
        assertEquals(400, http.put(path, mapOf("maxConsecutiveWorkDays" to 32), token, tag).status)
        assertEquals(400, http.put(path, mapOf("maxConsecutiveWorkDays" to 5.9), token, tag).status)
        assertEquals(400, http.put(path, mapOf("maxConsecutiveWorkDays" to "6"), token, tag).status)
        assertEquals(initial.header("ETag"), http.get(path, token).header("ETag"))
        val draft = UUID.randomUUID()
        val published = UUID.randomUUID()
        val otherDraft = UUID.randomUUID()
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,'DRAFT',FALSE,1)",
            draft, ward, "2026-11")
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,'PUBLISHED',TRUE,1)",
            published, ward, "2026-10")
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,'DRAFT',FALSE,1)",
            otherDraft, insertWard(), "2026-11")

        val changed = http.put(path, mapOf("maxConsecutiveWorkDays" to 6), token, tag)
        assertEquals(200, changed.status, changed.body)
        assertEquals("\"policy-1\"", changed.header("ETag"))
        assertEquals(6, changed.json!!.get("maxConsecutiveWorkDays").asInt())
        val updatedTag = mapOf("If-Match" to changed.header("ETag")!!)
        val noOp = http.put(path, mapOf("maxConsecutiveWorkDays" to 6), token, updatedTag)
        assertEquals(changed.header("ETag"), noOp.header("ETag"))
        assertEquals(412, http.put(path, mapOf("maxConsecutiveWorkDays" to 7), token, tag).status)
        fun revision(id: UUID) = jdbc.queryForObject("SELECT revision FROM rosters WHERE roster_id=?", Int::class.java, id)
        assertEquals(2, revision(draft))
        assertEquals(1, revision(published))
        assertEquals(1, revision(otherDraft))
        assertTrue(jdbc.queryForObject("SELECT needs_revalidation FROM rosters WHERE roster_id=?", Boolean::class.java, draft)!!)
    }

    @Test
    fun `rest day edits use roster revision and cannot conflict with approved annual leave`() {
        val ward = insertWard()
        val (admin, token) = userToken()
        val (member, memberToken) = userToken()
        grant(ward, admin, "WARD_ADMIN")
        grant(ward, member, "WARD_MEMBER")
        val nurse = insertNurse(ward, subject = member.toString())
        val (outside, _) = userToken()
        val outsideWard = insertWard()
        grant(outsideWard, outside, "WARD_ADMIN")
        val outsideNurse = insertNurse(outsideWard)
        val created = http.post("/api/v1/wards/$ward/rosters", mapOf("month" to "2026-11"), token, idem())
        val roster = created.json!!.get("rosterId").asString()
        val path = "/api/v1/rosters/$roster/rest-days"
        val tag = mapOf("If-Match" to created.header("ETag")!!)
        val rest = mapOf("nurseId" to nurse, "date" to "2026-11-05", "kind" to "SOF")
        fun body(items: List<Map<String, Any>>) = mapOf("items" to items)
        assertEquals(403, http.get(path, memberToken).status)
        assertEquals("INVALID_REST_DAYS", http.put(path, body(listOf(rest, rest)), token, tag).code)
        assertEquals("INVALID_REST_DAYS", http.put(path, body(listOf(rest + ("kind" to "VY"))), token, tag).code)
        assertEquals("DATE_OUTSIDE_ROSTER", http.put(path, body(listOf(rest + ("date" to "2026-12-05"))), token, tag).code)
        assertEquals("NURSE_NOT_FOUND", http.put(path, body(listOf(rest + ("nurseId" to outsideNurse))), token, tag).code)
        val stored = http.put(path, body(listOf(rest)), token, tag)
        assertEquals(200, stored.status, stored.body)
        val next = mapOf("If-Match" to stored.header("ETag")!!)
        assertEquals(412, http.put(path, body(emptyList()), token, tag).status)
        val read = http.get(path, token)
        assertEquals(stored.header("ETag"), read.header("ETag"))
        assertEquals("SOF", read.json!!.get("items").first().get("kind").asString())
        assertEquals(stored.header("ETag"), http.put(path, body(listOf(rest)), token, next).header("ETag"))

        val leave = mapOf("nurseId" to nurse, "kind" to "APPROVED_LEAVE",
            "startsAt" to "2026-11-04T15:00:00Z", "endsAt" to "2026-11-05T15:00:00Z")
        val absences = "/api/v1/rosters/$roster/absences"
        val conflict = http.post(absences, leave, token, next + idem())
        assertEquals(409, conflict.status, conflict.body)
        assertEquals("REST_DAY_CONFLICT", conflict.code)
        val removed = http.put(path, body(emptyList()), token, next)
        val approved = http.post(absences, leave, token, mapOf("If-Match" to removed.header("ETag")!!) + idem())
        assertEquals(201, approved.status, approved.body)
        val rejected = http.put(path, body(listOf(rest)), token, mapOf("If-Match" to approved.header("ETag")!!))
        assertEquals(409, rejected.status, rejected.body)
        assertEquals("REST_DAY_CONFLICT", rejected.code)
    }
}
