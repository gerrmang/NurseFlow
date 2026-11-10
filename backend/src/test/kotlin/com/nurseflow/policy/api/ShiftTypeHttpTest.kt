package com.nurseflow.policy.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class ShiftTypeHttpTest : ApiTestBase() {
    private lateinit var wardId: UUID
    private lateinit var admin: String
    private lateinit var member: String
    private lateinit var outsider: String

    @BeforeEach
    fun setup() {
        wardId = insertWard("근무 설정 병동")
        val (adminId, adminToken) = userToken("가상 수간호사")
        val (memberId, memberToken) = userToken("가상 간호사")
        grant(wardId, adminId, "WARD_ADMIN")
        grant(wardId, memberId, "WARD_MEMBER")
        admin = adminToken
        member = memberToken
        outsider = userToken("가상 외부인").second
    }

    private fun base() = "/api/v1/wards/$wardId/shift-types"

    private fun body(
        code: String = "N", name: String = "나이트", kind: String = "NIGHT", start: String = "22:00", end: String = "07:00",
        spans: Boolean = true, extra: Map<String, Any?> = emptyMap(),
    ): Map<String, Any?> =
        mapOf("code" to code, "displayName" to name, "kind" to kind, "startTime" to start, "endTime" to end, "spansMidnight" to spans) + extra

    private fun create(b: Map<String, Any?> = body()): JsonNode {
        val r = http.post(base(), b, admin)
        assertEquals(201, r.status, r.body)
        return r.json!!
    }

    private fun revision(): Int = http.get(base(), admin).json!!.get("policyRevision").asInt()

    @Test
    fun `permission table for every shift type operation`() {
        val id = create().get("shiftTypeId").asString()
        val put = body(code = "N", name = "나이트 변경")
        data class Case(val who: String, val token: String?, val list: Int, val write: Int, val missing: Int)
        listOf(
            Case("admin", admin, 200, 200, 200),
            Case("member", member, 200, 403, 403),
            Case("outsider", outsider, 404, 404, 404),
            Case("anonymous", null, 401, 401, 401),
        ).forEach { c ->
            assertEquals(c.list, http.get(base(), c.token).status, "list ${c.who}")
            assertEquals(c.write, http.put("${base()}/$id", put, c.token).status, "put ${c.who}")
            val created = http.post(base(), body(code = "X_${c.who.take(3).uppercase()}"), c.token).status
            assertEquals(if (c.who == "admin") 201 else c.write, created, "post ${c.who}")
            assertEquals(c.missing, http.delete("${base()}/$id", c.token).status, "delete ${c.who}")
        }
    }

    @Test
    fun `admin creates lists updates and deactivates a shift type`() {
        val day = create(body("D", "데이", "DAY", "07:00", "15:00", false, mapOf("breakMinutes" to 30)))
        assertEquals(480, day.get("durationMinutes").asInt())
        assertEquals(30, day.get("breakMinutes").asInt())
        assertEquals(450, day.get("recognizedMinutes").asInt())
        assertEquals(450, day.get("targetCreditMinutes").asInt())
        assertTrue(day.get("active").asBoolean())
        assertEquals("07:00", day.get("startTime").asString())
        val id = day.get("shiftTypeId").asString()

        val updated = http.put("${base()}/$id", body("D", "데이 근무", "DAY", "07:00", "16:00", false,
            mapOf("breakMinutes" to 60, "recognizedMinutes" to 480, "targetCreditMinutes" to 456)), admin)
        assertEquals(200, updated.status, updated.body)
        assertEquals(540, updated.json!!.get("durationMinutes").asInt())
        assertEquals(480, updated.json!!.get("recognizedMinutes").asInt())
        assertEquals(456, updated.json!!.get("targetCreditMinutes").asInt())

        // Members read the same values.
        val listed = http.get(base(), member).json!!
        assertEquals("데이 근무", listed.get("items").get(0).get("displayName").asString())

        val off = http.delete("${base()}/$id", admin)
        assertEquals(200, off.status)
        assertFalse(off.json!!.get("active").asBoolean())
        assertEquals(0, http.get(base(), member).json!!.get("items").size())
        assertEquals(1, http.get("${base()}?includeInactive=true", member).json!!.get("items").size())
        // Deactivating again is a no-op and does not bump the revision.
        val before = revision()
        assertEquals(200, http.delete("${base()}/$id", admin).status)
        assertEquals(before, revision())

        val back = http.put("${base()}/$id", body("D", "데이 근무", "DAY", "07:00", "15:00", false, mapOf("active" to true)), admin)
        assertTrue(back.json!!.get("active").asBoolean())
        assertEquals(1, http.get(base(), member).json!!.get("items").size())
        val actions = jdbc.queryForList("SELECT action FROM audit_events WHERE ward_id=? ORDER BY created_at", String::class.java, wardId)
        assertTrue(actions.containsAll(listOf("SHIFT_TYPE_CREATED", "SHIFT_TYPE_UPDATED", "SHIFT_TYPE_DEACTIVATED")), "$actions")
    }

    @Test
    fun `night crossing midnight counts duration across the date`() {
        val night = create(body(extra = mapOf("breakMinutes" to 60)))
        assertEquals(540, night.get("durationMinutes").asInt())
        assertEquals(480, night.get("recognizedMinutes").asInt())
        // Evening 15:00-23:00 does not cross midnight; 23:30-07:30 does.
        assertEquals(480, create(body("E", "이브닝", "EVENING", "15:00", "23:00", false)).get("durationMinutes").asInt())
        assertEquals(480, create(body("LATE", "늦은 밤", "NIGHT", "23:30", "07:30", true)).get("durationMinutes").asInt())
    }

    @Test
    fun `invalid times and minutes are rejected with stable codes`() {
        fun post(b: Map<String, Any?>) = http.post(base(), b, admin)
        assertEquals("INVALID_REQUEST", post(body(start = "7:00")).code)
        assertEquals("INVALID_REQUEST", post(body(start = "24:00")).code)
        assertEquals("INVALID_REQUEST", post(body(end = "07:00:00")).code)
        assertEquals("INVALID_REQUEST", post(body(code = "n")).code)
        assertEquals("INVALID_REQUEST", post(body(kind = "SPECIAL")).code)
        assertEquals("INVALID_REQUEST", post(body(extra = mapOf("breakMinutes" to 241))).code)
        assertEquals("INVALID_REQUEST", post(body(extra = mapOf("breakMinutes" to -1))).code)
        assertEquals("INVALID_REQUEST", post(body(extra = mapOf("recognizedMinutes" to 0))).code)

        post(body(start = "22:00", end = "07:00", spans = false)).let {
            assertEquals(422, it.status); assertEquals("SHIFT_SPANS_MIDNIGHT_MISMATCH", it.code)
        }
        post(body("D", "데이", "DAY", "07:00", "15:00", true)).let {
            assertEquals(422, it.status); assertEquals("SHIFT_SPANS_MIDNIGHT_MISMATCH", it.code)
        }
        post(body("D", "데이", "DAY", "07:00", "07:00", false)).let {
            assertEquals(422, it.status); assertEquals("INVALID_SHIFT_INTERVAL", it.code)
        }
        // 540 minutes minus 60 break leaves 480.
        post(body(extra = mapOf("breakMinutes" to 60, "recognizedMinutes" to 481))).let {
            assertEquals(422, it.status); assertEquals("INVALID_SHIFT_MINUTES", it.code)
        }
        assertEquals(201, post(body(extra = mapOf("breakMinutes" to 60, "recognizedMinutes" to 480, "targetCreditMinutes" to 0))).status)
        assertEquals(1, revision())
    }

    @Test
    fun `duplicate code is a conflict but overlapping times are allowed`() {
        create(body("D", "데이", "DAY", "07:00", "15:00", false))
        val dup = http.post(base(), body("D", "다른 데이", "DAY", "08:00", "16:00", false), admin)
        assertEquals(409, dup.status)
        assertEquals("SHIFT_TYPE_CODE_DUPLICATE", dup.code)
        assertEquals(201, http.post(base(), body("D2", "중간", "DAY", "08:00", "16:00", false), admin).status)

        val other = create(body("E", "이브닝", "EVENING", "15:00", "23:00", false)).get("shiftTypeId").asString()
        val clash = http.put("${base()}/$other", body("D", "이브닝", "EVENING", "15:00", "23:00", false), admin)
        assertEquals("SHIFT_TYPE_CODE_DUPLICATE", clash.code)
        assertEquals(200, http.put("${base()}/$other", body("E", "이브닝2", "EVENING", "15:00", "23:00", false), admin).status)
        assertEquals("SHIFT_TYPE_NOT_FOUND_IN_WARD", http.put("${base()}/${UUID.randomUUID()}", body(), admin).code)
    }

    @Test
    fun `another ward's shift type is not reachable through this ward`() {
        val otherWard = insertWard("다른 병동")
        val foreign = UUID.randomUUID()
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'D','데이','DAY','07:00:00','15:00:00',FALSE)""", foreign, otherWard)
        assertEquals(404, http.put("${base()}/$foreign", body(), admin).status)
        assertEquals(404, http.delete("${base()}/$foreign", admin).status)
        assertEquals(0, http.get(base(), admin).json!!.get("items").size())
    }

    @Test
    fun `policy revision increments on every change and starts at zero`() {
        assertEquals(0, revision())
        val id = create().get("shiftTypeId").asString()
        assertEquals(1, revision())
        http.put("${base()}/$id", body(name = "나이트2"), admin)
        assertEquals(2, revision())
        http.delete("${base()}/$id", admin)
        assertEquals(3, revision())
        http.post(base(), body(start = "7:00"), admin)
        http.post(base(), body(code = "D", kind = "DAY", start = "07:00", end = "15:00", spans = false), admin)
        assertEquals(4, revision())
        assertEquals(4, jdbc.queryForObject("SELECT revision FROM ward_policy_revisions WHERE ward_id=?", Int::class.java, wardId))
    }

    private fun roster(status: String, month: String, shiftTypeId: String): UUID {
        val rosterId = UUID.randomUUID()
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,?,?,1)",
            rosterId, wardId, month, status, status == "PUBLISHED")
        jdbc.update("INSERT INTO roster_slots(slot_id,roster_id,shift_date,shift_type_id,starts_at,ends_at) VALUES(?,?,?,?,?,?)",
            UUID.randomUUID(), rosterId, "$month-05", UUID.fromString(shiftTypeId),
            Instant.parse("2026-11-05T13:00:00Z").toString(), Instant.parse("2026-11-05T22:00:00Z").toString())
        return rosterId
    }

    private fun needsRevalidation(rosterId: UUID, token: String = admin): Boolean =
        http.get("/api/v1/rosters/$rosterId", token).json!!.get("needsRevalidation").asBoolean()

    @Test
    fun `changing a used shift type flags only draft rosters`() {
        val id = create().get("shiftTypeId").asString()
        val unrelated = create(body("D", "데이", "DAY", "07:00", "15:00", false)).get("shiftTypeId").asString()
        val draft = roster("DRAFT", "2026-11", id)
        val otherDraft = roster("DRAFT", "2026-12", unrelated)
        val published = roster("PUBLISHED", "2026-10", id)
        assertFalse(needsRevalidation(draft))

        // Name only: no flag.
        assertEquals(200, http.put("${base()}/$id", body(name = "야간"), admin).status)
        assertFalse(needsRevalidation(draft))

        assertEquals(200, http.put("${base()}/$id", body(name = "야간", end = "08:00"), admin).status)
        assertTrue(needsRevalidation(draft))
        assertFalse(needsRevalidation(otherDraft))
        assertFalse(needsRevalidation(published))
        val listed = http.get("/api/v1/wards/$wardId/rosters?month=2026-11", admin).json!!.get("items").get(0)
        assertTrue(listed.get("needsRevalidation").asBoolean())
    }

    @Test
    fun `deactivating a used type flags drafts and keeps existing slots readable`() {
        val id = create().get("shiftTypeId").asString()
        val draft = roster("DRAFT", "2026-11", id)
        val published = roster("PUBLISHED", "2026-10", id)
        assertEquals(200, http.delete("${base()}/$id", admin).status)
        assertTrue(needsRevalidation(draft))
        assertFalse(needsRevalidation(published))
        val slots = http.get("/api/v1/rosters/$draft", admin).json!!.get("slots")
        assertEquals(1, slots.size())
        assertEquals(id, slots.get(0).get("shiftTypeId").asString())
        // New requirements cannot use the inactive type.
        val req = http.put("/api/v1/rosters/$draft/requirements",
            mapOf("items" to listOf(mapOf("date" to "2026-11-05", "shiftTypeId" to id, "requiredCount" to 1, "requiredQualifications" to emptyList<String>()))),
            admin, mapOf("If-Match" to http.get("/api/v1/rosters/$draft", admin).header("ETag")!!))
        assertEquals("SHIFT_TYPE_NOT_FOUND", req.code)
    }

    @Test
    fun `concurrent creates with the same code yield one created and one conflict`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val ready = CountDownLatch(2)
            val go = CountDownLatch(1)
            val results = (1..2).map { n ->
                pool.submit<Int> {
                    ready.countDown(); go.await()
                    http.post(base(), body("D", "데이$n", "DAY", "07:00", "15:00", false), admin).status
                }
            }
            ready.await(); go.countDown()
            assertEquals(listOf(201, 409), results.map { it.get() }.sorted())
        } finally {
            pool.shutdownNow()
        }
        assertEquals(1, revision())
    }
}
