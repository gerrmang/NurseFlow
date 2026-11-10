package com.nurseflow.ward.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** All hospitals here are fictional rows of the local official-list copy. */
class WardCreationHttpTest : ApiTestBase() {
    @BeforeEach
    fun hospitals() {
        hospital("FAKE-H1", "가상대학교병원", "서울특별시 가상구 가상로 1", active = true)
        hospital("FAKE-H2", "폐업가상병원", "부산광역시 가상구 2", active = false)
    }

    private fun hospital(id: String, name: String, address: String, active: Boolean) {
        jdbc.update(
            """INSERT INTO hospitals(external_id,source,name,search_name,search_rank,category_name,address,active,
                                     dataset_version,last_import_id) VALUES(?,?,?,?,?,?,?,?,?,?)""",
            id, "HIRA", name, name, 0, "종합병원", address, active, "2026-09", UUID.randomUUID(),
        )
    }

    private fun body(externalId: String = "FAKE-H1", wardName: String = "가상 내과 병동", extra: Map<String, Any?> = emptyMap()) =
        mapOf("hospital" to mapOf("source" to "HIRA", "externalId" to externalId), "wardName" to wardName) + extra

    private fun create(token: String, body: Map<String, Any?> = body(), key: String = UUID.randomUUID().toString()) =
        http.post("/api/v1/wards", body, token, mapOf("Idempotency-Key" to key))

    @Test
    fun `the creator becomes the only admin and the hospital comes from the official list`() {
        val (_, token) = userToken("가상 수간호사")

        // The client sends only the id (surrounding spaces trimmed); the stored name and address come from the list.
        val created = create(token, body() + mapOf("hospital" to mapOf("source" to "HIRA", "externalId" to " FAKE-H1 ")))

        assertEquals(201, created.status, created.body)
        val ward = created.json!!
        val wardId = ward.get("wardId").asString()
        assertEquals("가상 내과 병동", ward.get("wardName").asString())
        assertEquals("가상대학교병원", ward.get("hospitalName").asString())
        assertEquals(listOf("WARD_ADMIN"), ward.get("roles").toList().map { it.asString() })
        assertTrue(ward.get("canViewWardCalendar").asBoolean())
        assertEquals(
            mapOf("hospital_external_id" to "FAKE-H1", "hospital_address" to "서울특별시 가상구 가상로 1"),
            jdbc.queryForMap("SELECT hospital_external_id, hospital_address FROM wards WHERE ward_id=?", UUID.fromString(wardId))
                .mapKeys { it.key.lowercase() },
        )
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM nurses WHERE ward_id=?", Int::class.java, UUID.fromString(wardId)))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='WARD_CREATED'", Int::class.java))

        val listed = http.get("/api/v1/wards", token).json!!.get("items")[0]
        assertEquals("가상대학교병원", listed.get("hospitalName").asString())
        assertEquals("가상대학교병원", http.get("/api/v1/me", token).json!!.get("memberships")[0].get("hospitalName").asString())
        assertEquals(200, http.get("/api/v1/wards/$wardId/rosters?month=2026-12", token).status)
    }

    @Test
    fun `same hospital and ward name by another admin is a separate ward with no shared access`() {
        val (_, first) = userToken("가상 관리자 1")
        val (_, second) = userToken("가상 관리자 2")

        val a = create(first).json!!.get("wardId").asString()
        val b = create(second).json!!.get("wardId").asString()

        assertNotEquals(a, b)
        assertEquals(404, http.get("/api/v1/wards/$a/rosters?month=2026-12", second).status)
        assertEquals(listOf(b), http.get("/api/v1/wards", second).json!!.get("items").toList().map { it.get("wardId").asString() })
    }

    @Test
    fun `hospitals outside the active official list are rejected`() {
        val (_, token) = userToken()

        assertEquals("HOSPITAL_NOT_FOUND", create(token, body("FAKE-NONE")).code)
        assertEquals("HOSPITAL_NOT_FOUND", create(token, body("FAKE-H2")).code)
        assertEquals(422, create(token, body("FAKE-H2")).status)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wards", Int::class.java))
    }

    @Test
    fun `without an imported list the hospital cannot be checked`() {
        jdbc.update("DELETE FROM hospitals")
        val (_, token) = userToken()

        val response = create(token)

        assertEquals(503, response.status)
        assertEquals("HOSPITAL_SEARCH_NOT_CONFIGURED", response.code)
    }

    @Test
    fun `retrying with the same key returns the first ward, a different body or user conflicts`() {
        val (_, token) = userToken()
        val (_, other) = userToken("가상 다른 사용자")
        val key = UUID.randomUUID().toString()

        val first = create(token, key = key)
        // Only surrounding spaces differ; the stored ward would be the same, so this is a replay.
        val retry = create(token, body(externalId = " FAKE-H1", wardName = "  가상 내과 병동 "), key = key)
        val changed = create(token, body(wardName = "다른 병동"), key = key)
        val stolen = create(other, key = key)

        assertEquals(201, retry.status)
        assertEquals(first.json!!.get("wardId").asString(), retry.json!!.get("wardId").asString())
        assertEquals("IDEMPOTENCY_KEY_REUSED", changed.code)
        assertEquals(409, stolen.status)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wards", Int::class.java))
    }

    @Test
    fun `concurrent requests with the same key create one ward`() {
        val (_, token) = userToken()
        val key = UUID.randomUUID().toString()
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(4)
        try {
            val results = (1..4).map { executor.submit<String> { start.await(); create(token, key = key).let { "${it.status}:${it.json?.get("wardId")?.asString()}" } } }
            start.countDown()
            val responses = results.map { it.get(10, TimeUnit.SECONDS) }

            assertEquals(1, responses.toSet().size, responses.toString())
            assertTrue(responses.first().startsWith("201:"), responses.toString())
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wards", Int::class.java))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `an invitation preview names the hospital so same-named wards can be told apart`() {
        val (_, admin) = userToken("가상 관리자")
        val (_, nurse) = userToken("가상 간호사")
        val wardId = create(admin).json!!.get("wardId").asString()
        val token = http.post("/api/v1/wards/$wardId/invitations", mapOf("role" to "WARD_MEMBER"), admin).json!!.get("token").asString()

        val preview = http.post("/api/v1/invitations/preview", mapOf("token" to token), nurse)

        assertEquals(200, preview.status, preview.body)
        assertEquals("가상대학교병원", preview.json!!.get("hospitalName").asString())
    }

    @Test
    fun `invalid input is rejected before anything is stored`() {
        val (_, token) = userToken()

        assertEquals(listOf("wardName"), create(token, body(wardName = " ")).fieldNames)
        assertEquals(listOf("hospital"), create(token, mapOf("wardName" to "병동")).fieldNames)
        assertEquals("INVALID_REQUEST", create(token, body() + mapOf("hospital" to mapOf("source" to "MANUAL", "externalId" to "x"))).code)
        assertEquals("INVALID_TIME_ZONE", create(token, body(extra = mapOf("timeZone" to "Mars/Base"))).code)
        assertEquals(400, http.post("/api/v1/wards", body(), token).status)
        assertEquals(401, http.post("/api/v1/wards", body(), null, idem()).status)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wards", Int::class.java))
    }
}
