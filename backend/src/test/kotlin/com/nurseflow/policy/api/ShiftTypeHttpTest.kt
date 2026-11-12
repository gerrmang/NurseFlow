package com.nurseflow.policy.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.provider.ValueSource
import org.junit.jupiter.params.ParameterizedTest
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
        val r = postType(b, admin)
        assertEquals(201, r.status, r.body)
        return r.json!!
    }

    private fun postType(b: Map<String, Any?>, token: String?, key: String = UUID.randomUUID().toString()) =
        http.post(base(), b, token, mapOf("Idempotency-Key" to key))

    @Test
    fun `retrying a create with the same key returns the first type`() {
        val key = UUID.randomUUID().toString()
        val first = postType(body(code = "R1"), admin, key)
        val retry = postType(body(code = "R1"), admin, key)

        assertEquals(201, first.status, first.body)
        assertEquals(201, retry.status, retry.body)
        assertEquals(first.json!!.get("shiftTypeId").asString(), retry.json!!.get("shiftTypeId").asString())
        assertEquals("IDEMPOTENCY_KEY_REUSED", postType(body(code = "R2"), admin, key).code)
        assertEquals(400, http.post(base(), body(code = "R3"), admin).status)
    }

    @Test
    fun `OTHER kind cannot be entered until its rest rules are decided`() {
        assertEquals("INVALID_REQUEST", postType(body(code = "S1", kind = "OTHER"), admin).code)
    }

    @Test
    fun `changing hours rewrites draft slot times and bumps the draft revision, published slots stay`() {
        val id = create().get("shiftTypeId").asString()
        val draft = roster("DRAFT", "2026-11", id)
        val published = roster("PUBLISHED", "2026-12", id)
        fun slotTimes(rosterId: UUID) = jdbc.queryForMap("SELECT starts_at, ends_at FROM roster_slots WHERE roster_id=?", rosterId)
            .mapKeys { it.key.lowercase() }.mapValues { it.value.toString() }
        fun rosterRevision(rosterId: UUID) = jdbc.queryForObject("SELECT revision FROM rosters WHERE roster_id=?", Int::class.java, rosterId)
        val publishedBefore = slotTimes(published)

        val updated = http.put("${base()}/$id", body(code = "N", kind = "NIGHT", start = "21:00", end = "06:00", spans = true), admin, current())

        assertEquals(200, updated.status, updated.body)
        // 2026-11-05 21:00 Asia/Seoul = 12:00Z, ends next day 06:00 KST = 21:00Z.
        assertEquals(mapOf("starts_at" to "2026-11-05T12:00:00Z", "ends_at" to "2026-11-05T21:00:00Z"), slotTimes(draft))
        assertEquals(2, rosterRevision(draft))
        assertEquals(publishedBefore, slotTimes(published))
        assertEquals(1, rosterRevision(published))
    }

    private fun revision(): Int = http.get(base(), admin).json!!.get("policyRevision").asInt()

    /** If-Match with the ward policy ETag as of now, as a client that just reloaded the list would send. */
    private fun current() = mapOf("If-Match" to http.get(base(), admin).header("ETag")!!)

    private fun confirmWith(rosterId: UUID, etag: String) = http.post("/api/v1/rosters/$rosterId/confirm", emptyMap<String, Any>(),
        admin, mapOf("If-Match" to etag, "Idempotency-Key" to UUID.randomUUID().toString()))

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = ["kind", "code", "inactive", "break", "recognized", "target credit", "hours", "delete"])
    fun `every change that affects drafts rejects confirming from the view before it`(change: String) {
        val id = create().get("shiftTypeId").asString()
        val draft = roster("DRAFT", "2026-11", id)
        val before = http.get("/api/v1/rosters/$draft", admin).header("ETag")!!

        val response = when (change) {
            "kind" -> http.put("${base()}/$id", body(kind = "EVENING"), admin, current())
            "code" -> http.put("${base()}/$id", body(code = "N2"), admin, current())
            "inactive" -> http.put("${base()}/$id", body(extra = mapOf("active" to false)), admin, current())
            "break" -> http.put("${base()}/$id", body(extra = mapOf("breakMinutes" to 60)), admin, current())
            "recognized" -> http.put("${base()}/$id", body(extra = mapOf("recognizedMinutes" to 400)), admin, current())
            "target credit" -> http.put("${base()}/$id", body(extra = mapOf("targetCreditMinutes" to 300)), admin, current())
            "hours" -> http.put("${base()}/$id", body(end = "08:00"), admin, current())
            else -> http.delete("${base()}/$id", admin, current())
        }
        assertEquals(200, response.status, response.body)

        val stale = confirmWith(draft, before)
        assertEquals(412, stale.status, stale.body)
        assertEquals("STALE_ROSTER_REVISION", stale.code)
        assertTrue(needsRevalidation(draft))
        // Reloading gives the current revision; the flag stays until a confirmation under the current policy.
        assertTrue(http.get("/api/v1/rosters/$draft", admin).header("ETag") != before)
    }

    @Test
    fun `draft slots follow a code and kind change while published slots keep theirs`() {
        val id = create().get("shiftTypeId").asString()
        val draft = roster("DRAFT", "2026-11", id)
        val published = roster("PUBLISHED", "2026-10", id)
        fun codeAndKind(rosterId: UUID) = jdbc.queryForMap("SELECT shift_code, shift_kind FROM roster_slots WHERE roster_id=?", rosterId)
            .mapKeys { it.key.lowercase() }

        assertEquals(200, http.put("${base()}/$id", body(code = "E", kind = "EVENING"), admin, current()).status)

        assertEquals(mapOf("shift_code" to "E", "shift_kind" to "EVENING"), codeAndKind(draft))
        assertEquals(mapOf("shift_code" to "N", "shift_kind" to "NIGHT"), codeAndKind(published))
    }

    @Test
    fun `an hours change that would end a draft slot before it starts on a daylight-saving day is rolled back`() {
        jdbc.update("UPDATE wards SET time_zone='America/New_York' WHERE ward_id=?", wardId)
        val id = create(body("D", "데이", "DAY", "01:00", "02:00", false)).get("shiftTypeId").asString()
        val draft = UUID.randomUUID()
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,'2026-03','DRAFT',FALSE,1)", draft, wardId)
        // 2026-03-08 01:00-02:00 EST.
        jdbc.update("""INSERT INTO roster_slots(slot_id,roster_id,shift_date,shift_type_id,shift_code,shift_kind,starts_at,ends_at)
            VALUES(?,?,'2026-03-08',?,'D','DAY','2026-03-08T06:00:00Z','2026-03-08T07:00:00Z')""", UUID.randomUUID(), draft, UUID.fromString(id))
        val policyBefore = revision()

        // 02:30 does not exist that night (clocks jump 02:00 -> 03:00), so the start moves to 03:30, after the 03:00 end.
        val response = http.put("${base()}/$id", body("D", "데이", "DAY", "02:30", "03:00", false), admin, current())

        assertEquals(422, response.status, response.body)
        assertEquals("INVALID_SHIFT_INTERVAL", response.code)
        assertEquals("01:00", http.get(base(), admin).json!!.get("items")[0].get("startTime").asString().take(5))
        assertEquals(mapOf("starts_at" to "2026-03-08T06:00:00Z", "ends_at" to "2026-03-08T07:00:00Z"),
            jdbc.queryForMap("SELECT starts_at, ends_at FROM roster_slots WHERE roster_id=?", draft).mapKeys { it.key.lowercase() }
                .mapValues { it.value.toString() })
        assertEquals(1, jdbc.queryForObject("SELECT revision FROM rosters WHERE roster_id=?", Int::class.java, draft))
        assertEquals(policyBefore, revision())
    }

    @Test
    fun `roster responses carry the code and kind stored with each slot`() {
        val id = create().get("shiftTypeId").asString()
        val draft = roster("DRAFT", "2026-11", id)
        val published = roster("PUBLISHED", "2026-10", id)

        assertEquals(200, http.put("${base()}/$id", body(code = "E", kind = "EVENING"), admin, current()).status)

        fun slot(json: JsonNode) = json.get("slots")[0].let { it.get("shiftCode").asString() to it.get("shiftKind").asString() }
        assertEquals("N" to "NIGHT", slot(http.get("/api/v1/rosters/$published", admin).json!!))
        assertEquals("N" to "NIGHT", slot(http.get("/api/v1/wards/$wardId/published-rosters?month=2026-10", member).json!!))
        assertEquals("E" to "EVENING", slot(http.get("/api/v1/rosters/$draft", admin).json!!))
    }

    @Test
    fun `a change from a stale policy revision is refused instead of overwriting another admin's change`() {
        val id = create().get("shiftTypeId").asString()
        val seen = current()
        // Another admin saves first.
        val first = http.put("${base()}/$id", body(name = "야간 A"), admin, seen)
        assertEquals(200, first.status, first.body)
        assertEquals(http.get(base(), admin).header("ETag"), first.header("ETag"))

        val stale = http.put("${base()}/$id", body(name = "야간 B"), admin, seen)
        assertEquals(412, stale.status, stale.body)
        assertEquals("STALE_POLICY_REVISION", stale.code)
        assertEquals("STALE_POLICY_REVISION", http.delete("${base()}/$id", admin, seen).code)
        assertEquals("INVALID_REQUEST", http.put("${base()}/$id", body(name = "야간 C"), admin).code)
        assertEquals("야간 A", http.get(base(), admin).json!!.get("items")[0].get("displayName").asString())

        // Deactivating an already inactive type writes nothing, so even a stale view gets the no-op 200.
        assertEquals(200, http.delete("${base()}/$id", admin, current()).status)
        assertEquals(200, http.delete("${base()}/$id", admin, seen).status)
    }

    @Test
    fun `a create retried after the type changed replays the first revision, so saving from it is refused`() {
        val key = mapOf("Idempotency-Key" to UUID.randomUUID().toString())
        val created = http.post(base(), body(), admin, key)
        val id = created.json!!.get("shiftTypeId").asString()
        assertEquals(200, http.put("${base()}/$id", body(name = "다른 관리자가 바꾼 이름"), admin, current()).status)

        // The first response was lost; the retry replays it with the revision it was made at.
        val retried = http.post(base(), body(), admin, key)
        assertEquals(201, retried.status, retried.body)
        assertEquals(created.header("ETag"), retried.header("ETag"))
        assertEquals("STALE_POLICY_REVISION",
            http.put("${base()}/$id", body(name = "재시도 화면에서 저장"), admin, mapOf("If-Match" to retried.header("ETag")!!)).code)
    }

    @Test
    fun `preview reports the drafts a change would flag without writing anything`() {
        val id = create().get("shiftTypeId").asString()
        val draft = roster("DRAFT", "2026-11", id)
        roster("PUBLISHED", "2026-10", id)
        val etagBefore = http.get("/api/v1/rosters/$draft", admin).header("ETag")
        val policyBefore = revision()

        val preview = http.post("${base()}/$id/preview", body(kind = "EVENING", end = "08:00"), admin)

        assertEquals(200, preview.status, preview.body)
        val json = preview.json!!
        assertTrue(json.get("flagsDrafts").asBoolean())
        assertTrue(json.get("rewritesDraftSlotTimes").asBoolean())
        assertEquals("EVENING", json.get("shiftType").get("kind").asString())
        assertEquals(policyBefore, json.get("policyRevision").asInt())
        val affected = json.get("affectedDrafts")
        assertEquals(1, affected.size())
        assertEquals(draft.toString(), affected[0].get("rosterId").asString())
        assertEquals(1, affected[0].get("slots").asInt())
        assertEquals(0, affected[0].get("assignedSlots").asInt())
        assertEquals(1, json.get("publishedRostersUsingType").asInt())
        // Nothing was saved.
        assertEquals(policyBefore, revision())
        assertEquals(etagBefore, http.get("/api/v1/rosters/$draft", admin).header("ETag"))
        assertEquals("NIGHT", http.get(base(), admin).json!!.get("items")[0].get("kind").asString())

        val nameOnly = http.post("${base()}/$id/preview", body(name = "야간"), admin).json!!
        assertFalse(nameOnly.get("flagsDrafts").asBoolean())
        assertEquals(0, nameOnly.get("affectedDrafts").size())
        assertEquals(403, http.post("${base()}/$id/preview", body(name = "야간"), member).status)
    }

    @Test
    fun `preview refuses what the save would refuse, including a daylight-saving day`() {
        jdbc.update("UPDATE wards SET time_zone='America/New_York' WHERE ward_id=?", wardId)
        val id = create(body("D", "데이", "DAY", "01:00", "02:00", false)).get("shiftTypeId").asString()
        val draft = UUID.randomUUID()
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,'2026-03','DRAFT',FALSE,1)", draft, wardId)
        jdbc.update("""INSERT INTO roster_slots(slot_id,roster_id,shift_date,shift_type_id,shift_code,shift_kind,starts_at,ends_at)
            VALUES(?,?,'2026-03-08',?,'D','DAY','2026-03-08T06:00:00Z','2026-03-08T07:00:00Z')""", UUID.randomUUID(), draft, UUID.fromString(id))

        assertEquals("INVALID_SHIFT_INTERVAL", http.post("${base()}/$id/preview", body("D", "데이", "DAY", "02:30", "03:00", false), admin).code)
        create(body("E", "이브닝", "EVENING", "15:00", "23:00", false))
        assertEquals("SHIFT_TYPE_CODE_DUPLICATE", http.post("${base()}/$id/preview", body("E", "데이", "DAY", "01:00", "02:00", false), admin).code)
    }

    @Test
    fun `a name-only change keeps the draft revision`() {
        val id = create().get("shiftTypeId").asString()
        val draft = roster("DRAFT", "2026-11", id)
        val before = http.get("/api/v1/rosters/$draft", admin).header("ETag")!!

        assertEquals(200, http.put("${base()}/$id", body(name = "야간"), admin, current()).status)

        assertEquals(before, http.get("/api/v1/rosters/$draft", admin).header("ETag"))
        assertFalse(needsRevalidation(draft))
    }

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
            assertEquals(c.write, http.put("${base()}/$id", put, c.token, current()).status, "put ${c.who}")
            val created = postType(body(code = "X_${c.who.take(3).uppercase()}"), c.token).status
            assertEquals(if (c.who == "admin") 201 else c.write, created, "post ${c.who}")
            assertEquals(c.missing, http.delete("${base()}/$id", c.token, current()).status, "delete ${c.who}")
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
            mapOf("breakMinutes" to 60, "recognizedMinutes" to 480, "targetCreditMinutes" to 456)), admin, current())
        assertEquals(200, updated.status, updated.body)
        assertEquals(540, updated.json!!.get("durationMinutes").asInt())
        assertEquals(480, updated.json!!.get("recognizedMinutes").asInt())
        assertEquals(456, updated.json!!.get("targetCreditMinutes").asInt())

        // Members read the same values.
        val listed = http.get(base(), member).json!!
        assertEquals("데이 근무", listed.get("items").get(0).get("displayName").asString())

        val off = http.delete("${base()}/$id", admin, current())
        assertEquals(200, off.status)
        assertFalse(off.json!!.get("active").asBoolean())
        assertEquals(0, http.get(base(), member).json!!.get("items").size())
        assertEquals(1, http.get("${base()}?includeInactive=true", member).json!!.get("items").size())
        // Deactivating again is a no-op and does not bump the revision.
        val before = revision()
        assertEquals(200, http.delete("${base()}/$id", admin, current()).status)
        assertEquals(before, revision())

        val back = http.put("${base()}/$id", body("D", "데이 근무", "DAY", "07:00", "15:00", false, mapOf("active" to true)), admin, current())
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
        fun post(b: Map<String, Any?>) = postType(b, admin)
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
        val dup = postType(body("D", "다른 데이", "DAY", "08:00", "16:00", false), admin)
        assertEquals(409, dup.status)
        assertEquals("SHIFT_TYPE_CODE_DUPLICATE", dup.code)
        assertEquals(201, postType(body("D2", "중간", "DAY", "08:00", "16:00", false), admin).status)

        val other = create(body("E", "이브닝", "EVENING", "15:00", "23:00", false)).get("shiftTypeId").asString()
        val clash = http.put("${base()}/$other", body("D", "이브닝", "EVENING", "15:00", "23:00", false), admin, current())
        assertEquals("SHIFT_TYPE_CODE_DUPLICATE", clash.code)
        assertEquals(200, http.put("${base()}/$other", body("E", "이브닝2", "EVENING", "15:00", "23:00", false), admin, current()).status)
        assertEquals("SHIFT_TYPE_NOT_FOUND_IN_WARD", http.put("${base()}/${UUID.randomUUID()}", body(), admin, current()).code)
    }

    @Test
    fun `another ward's shift type is not reachable through this ward`() {
        val otherWard = insertWard("다른 병동")
        val foreign = UUID.randomUUID()
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'D','데이','DAY','07:00:00','15:00:00',FALSE)""", foreign, otherWard)
        assertEquals(404, http.put("${base()}/$foreign", body(), admin, current()).status)
        assertEquals(404, http.delete("${base()}/$foreign", admin, current()).status)
        assertEquals(0, http.get(base(), admin).json!!.get("items").size())
    }

    @Test
    fun `policy revision increments on every change and starts at zero`() {
        assertEquals(0, revision())
        val id = create().get("shiftTypeId").asString()
        assertEquals(1, revision())
        http.put("${base()}/$id", body(name = "나이트2"), admin, current())
        assertEquals(2, revision())
        http.delete("${base()}/$id", admin, current())
        assertEquals(3, revision())
        postType(body(start = "7:00"), admin)
        postType(body(code = "D", kind = "DAY", start = "07:00", end = "15:00", spans = false), admin)
        assertEquals(4, revision())
        assertEquals(4, jdbc.queryForObject("SELECT revision FROM ward_policy_revisions WHERE ward_id=?", Int::class.java, wardId))
    }

    private fun roster(status: String, month: String, shiftTypeId: String): UUID {
        val rosterId = UUID.randomUUID()
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,?,?,1)",
            rosterId, wardId, month, status, status == "PUBLISHED")
        // Like RosterJdbcRepository.insertSlot: the slot keeps the type's code and kind as created.
        jdbc.update("""INSERT INTO roster_slots(slot_id,roster_id,shift_date,shift_type_id,shift_code,shift_kind,starts_at,ends_at)
            SELECT ?,?,?,shift_type_id,code,kind,?,? FROM shift_types WHERE shift_type_id=?""",
            UUID.randomUUID(), rosterId, "$month-05",
            Instant.parse("2026-11-05T13:00:00Z").toString(), Instant.parse("2026-11-05T22:00:00Z").toString(), UUID.fromString(shiftTypeId))
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
        assertEquals(200, http.put("${base()}/$id", body(name = "야간"), admin, current()).status)
        assertFalse(needsRevalidation(draft))

        assertEquals(200, http.put("${base()}/$id", body(name = "야간", end = "08:00"), admin, current()).status)
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
        assertEquals(200, http.delete("${base()}/$id", admin, current()).status)
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
                    postType(body("D", "데이$n", "DAY", "07:00", "15:00", false), admin).status
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
