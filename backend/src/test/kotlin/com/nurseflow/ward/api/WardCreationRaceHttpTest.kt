package com.nurseflow.ward.api

import com.nurseflow.common.idempotency.MutationReplayRepository
import com.nurseflow.support.ApiTestBase
import com.nurseflow.support.HttpTestClient
import com.nurseflow.ward.persistence.repository.WardJdbcRepository
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
import tools.jackson.databind.json.JsonMapper
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Forces the two orderings the same-key code paths exist for, instead of hoping a burst of threads hits them.
 * Test-only subclasses pause the first request after it wrote its replay row (still uncommitted, still holding
 * its locks) until the second request has passed its own first replay lookup.
 */
@Import(WardCreationRaceHttpTest.Gates::class)
class WardCreationRaceHttpTest : ApiTestBase() {
    @Autowired lateinit var gate: Gate

    /** First recordReplay waits for [release]; [secondArrived] fires when the second request reaches [point]. */
    class Gate {
        @Volatile var point: String = ""
        @Volatile var firstPaused = CountDownLatch(1)
        @Volatile var secondArrived = CountDownLatch(1)
        @Volatile var release = CountDownLatch(1)
        val replays = AtomicInteger()
        val locks = AtomicInteger()

        /** Latches are single-use, so every test starts with fresh ones. */
        fun reset(at: String) {
            firstPaused = CountDownLatch(1)
            secondArrived = CountDownLatch(1)
            release = CountDownLatch(1)
            replays.set(0)
            locks.set(0)
            point = at
        }
    }

    /** Test-only subclasses must be open: Spring proxies @Repository beans for exception translation. */
    open class GatedReplays(jdbc: JdbcTemplate, mapper: JsonMapper, private val gate: Gate) : MutationReplayRepository(jdbc, mapper) {
        override fun recordReplay(key: UUID, operation: String, requestHash: String, response: Any) {
            if (gate.point.isEmpty()) return super.recordReplay(key, operation, requestHash, response)
            if (gate.replays.incrementAndGet() == 1) {
                super.recordReplay(key, operation, requestHash, response)
                gate.firstPaused.countDown()
                gate.release.await(10, TimeUnit.SECONDS)
            } else {
                if (gate.point == "replay") gate.secondArrived.countDown()
                super.recordReplay(key, operation, requestHash, response)
            }
        }
    }

    open class GatedWards(jdbc: JdbcTemplate, private val gate: Gate) : WardJdbcRepository(jdbc) {
        override fun lockUser(userId: UUID): Boolean {
            if (gate.point == "lock" && gate.locks.incrementAndGet() == 2) gate.secondArrived.countDown()
            return super.lockUser(userId)
        }
    }

    @TestConfiguration
    class Gates {
        @Bean fun gate() = Gate()

        @Bean @Primary
        fun gatedReplays(jdbc: JdbcTemplate, mapper: JsonMapper, gate: Gate) = GatedReplays(jdbc, mapper, gate)

        @Bean @Primary
        fun gatedWards(jdbc: JdbcTemplate, gate: Gate) = GatedWards(jdbc, gate)
    }

    @BeforeEach
    fun hospital() {
        jdbc.update(
            """INSERT INTO hospitals(external_id,source,name,search_name,search_rank,category_name,address,active,
                                     dataset_version,last_import_id) VALUES(?,?,?,?,?,?,?,?,?,?)""",
            "FAKE-H1", "HIRA", "가상대학교병원", "가상대학교병원", 0, "종합병원", "서울특별시 가상구 1", true, "2026-09", UUID.randomUUID(),
        )
    }

    @AfterEach
    fun open() {
        gate.point = ""
        gate.release.countDown()
    }

    private fun create(token: String, key: String): CompletableFuture<HttpTestClient.Response> = CompletableFuture.supplyAsync {
        http.post(
            "/api/v1/wards",
            mapOf("hospital" to mapOf("source" to "HIRA", "externalId" to "FAKE-H1"), "wardName" to "가상 내과 병동"),
            token,
            mapOf("Idempotency-Key" to key),
        )
    }

    private fun count(table: String) = jdbc.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java)

    @Test
    fun `a same-key retry that waited on the account lock replays the first ward`() {
        gate.reset("lock")
        val (_, token) = userToken()
        val key = UUID.randomUUID().toString()

        val first = create(token, key)
        assertTrue(gate.firstPaused.await(10, TimeUnit.SECONDS), "first request should pause before commit")
        // The retry's first replay lookup runs now and misses; it then queues on the account lock.
        val retry = create(token, key)
        assertTrue(gate.secondArrived.await(10, TimeUnit.SECONDS), "retry should reach the account lock")
        gate.release.countDown()

        val a = first.get(10, TimeUnit.SECONDS)
        val b = retry.get(10, TimeUnit.SECONDS)
        assertEquals(201, a.status, a.body)
        assertEquals(201, b.status, b.body)
        assertEquals(a.json!!.get("wardId").asString(), b.json!!.get("wardId").asString())
        assertEquals(1, count("wards"))
    }

    @Test
    fun `two accounts racing with one key leave only the winner's rows`() {
        gate.reset("replay")
        val (winner, winnerToken) = userToken("가상 관리자 1")
        val (_, loserToken) = userToken("가상 관리자 2")
        val key = UUID.randomUUID().toString()

        val first = create(winnerToken, key)
        assertTrue(gate.firstPaused.await(10, TimeUnit.SECONDS), "first request should pause before commit")
        // Different users lock different rows, so the second request writes its ward too and only meets the
        // first one on the replay row's primary key.
        val second = create(loserToken, key)
        assertTrue(gate.secondArrived.await(10, TimeUnit.SECONDS), "second request should reach the replay insert")
        gate.release.countDown()

        val a = first.get(10, TimeUnit.SECONDS)
        val b = second.get(10, TimeUnit.SECONDS)
        assertEquals(201, a.status, a.body)
        assertEquals(409, b.status, b.body)
        assertEquals("IDEMPOTENCY_KEY_REUSED", b.code)
        assertEquals(1, count("wards"))
        assertEquals(1, count("ward_memberships"))
        assertEquals(winner.toString(), jdbc.queryForObject("SELECT subject FROM ward_memberships", String::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_events WHERE action='WARD_CREATED'", Int::class.java))
        assertEquals(1, count("mutation_replays"))
    }
}
