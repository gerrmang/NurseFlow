package com.nurseflow.policy.api

import com.nurseflow.policy.application.model.ShiftTypeRow
import com.nurseflow.policy.application.service.ShiftTypeService
import com.nurseflow.policy.persistence.repository.ShiftTypeJdbcRepository
import com.nurseflow.support.ApiTestBase
import com.nurseflow.support.HttpTestClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Forces another admin's change to commit right after a list or preview has read the types: the ETag it answers
 * with must not be newer than the data it shows, or a save from that view would overwrite the change unchecked.
 * Test-only subclass, no production hook: the first armed read of the types pauses after it returns.
 */
@Import(ShiftTypePolicyEtagRaceHttpTest.Gates::class)
class ShiftTypePolicyEtagRaceHttpTest : ApiTestBase() {
    @Autowired lateinit var gate: Gate

    class Gate {
        @Volatile var armed = false
        @Volatile var paused = CountDownLatch(1)
        @Volatile var release = CountDownLatch(1)

        fun arm() {
            paused = CountDownLatch(1)
            release = CountDownLatch(1)
            armed = true
        }

        fun pauseOnce() {
            if (!armed) return
            armed = false
            paused.countDown()
            release.await(10, TimeUnit.SECONDS)
        }
    }

    /** Must be open: Spring proxies @Repository beans for exception translation. */
    open class GatedShiftTypes(jdbc: JdbcTemplate, private val gate: Gate) : ShiftTypeJdbcRepository(jdbc) {
        override fun list(wardId: UUID, includeInactive: Boolean): List<ShiftTypeRow> =
            super.list(wardId, includeInactive).also { gate.pauseOnce() }

        override fun find(wardId: UUID, shiftTypeId: UUID): ShiftTypeRow? = super.find(wardId, shiftTypeId).also { gate.pauseOnce() }
    }

    @TestConfiguration
    class Gates {
        @Bean fun gate() = Gate()

        @Bean @Primary
        fun gatedShiftTypes(jdbc: JdbcTemplate, gate: Gate) = GatedShiftTypes(jdbc, gate)
    }

    private lateinit var wardId: UUID
    private lateinit var admin: String
    private lateinit var shiftTypeId: String

    private fun base() = "/api/v1/wards/$wardId/shift-types"

    private fun body(name: String) = mapOf(
        "code" to "N", "displayName" to name, "kind" to "NIGHT", "startTime" to "22:00", "endTime" to "07:00", "spansMidnight" to true,
    )

    @BeforeEach
    fun setup() {
        wardId = insertWard("근무 설정 병동")
        val (adminId, token) = userToken("가상 수간호사")
        grant(wardId, adminId, "WARD_ADMIN")
        admin = token
        shiftTypeId = http.post(base(), body("나이트"), admin, mapOf("Idempotency-Key" to UUID.randomUUID().toString(), "If-Match" to ShiftTypeService.policyEtag(0)))
            .json!!.get("shiftTypeId").asString()
    }

    @AfterEach
    fun open() {
        gate.armed = false
        gate.release.countDown()
    }

    /** While [read] is paused after reading the types, another admin saves; returns what [read] answered. */
    private fun withChangeDuringRead(read: () -> HttpTestClient.Response): HttpTestClient.Response {
        val etag = http.get(base(), admin).header("ETag")!!
        gate.arm()
        val reading = CompletableFuture.supplyAsync(read)
        assertTrue(gate.paused.await(10, TimeUnit.SECONDS), "the read should pause after reading the types")
        val other = http.put("${base()}/$shiftTypeId", body("다른 관리자가 바꾼 이름"), admin, mapOf("If-Match" to etag))
        assertEquals(200, other.status, other.body)
        gate.release.countDown()
        return reading.get(10, TimeUnit.SECONDS)
    }

    @Test
    fun `a list read before another admin's change never answers with the newer ETag`() {
        val listed = withChangeDuringRead { http.get(base(), admin) }

        assertEquals("나이트", listed.json!!.get("items")[0].get("displayName").asString())
        val saved = http.put("${base()}/$shiftTypeId", body("목록 화면에서 저장"), admin, mapOf("If-Match" to listed.header("ETag")!!))
        assertEquals("STALE_POLICY_REVISION", saved.code)
    }

    @Test
    fun `a preview computed before another admin's change never reports the newer revision`() {
        val preview = withChangeDuringRead { http.post("${base()}/$shiftTypeId/preview", body("미리보기 이름"), admin) }
        assertEquals(200, preview.status, preview.body)

        val saved = http.put("${base()}/$shiftTypeId", body("미리보기 이름"), admin,
            mapOf("If-Match" to "\"policy-${preview.json!!.get("policyRevision").asInt()}\""))
        assertEquals("STALE_POLICY_REVISION", saved.code)
    }
}
