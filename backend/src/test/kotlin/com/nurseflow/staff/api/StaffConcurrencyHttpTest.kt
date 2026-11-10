package com.nurseflow.staff.api

import com.nurseflow.staff.persistence.repository.StaffJdbcRepository
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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Forces the interleavings behind PR #37 review findings with test-only subclasses (no production hooks):
 * the first request pauses while holding its lock until the second request has reached the same lock.
 */
@Import(StaffConcurrencyHttpTest.Gates::class)
class StaffConcurrencyHttpTest : ApiTestBase() {
    @Autowired lateinit var gate: Gate
    private lateinit var wardId: UUID
    private lateinit var admin: String
    private lateinit var admin2: String

    class Gate {
        @Volatile var point = ""
        @Volatile var firstHolding = CountDownLatch(1)
        @Volatile var secondArrived = CountDownLatch(1)
        @Volatile var release = CountDownLatch(1)
        val calls = AtomicInteger()
        val arrivals = AtomicInteger()

        fun arm(at: String) {
            firstHolding = CountDownLatch(1)
            secondArrived = CountDownLatch(1)
            release = CountDownLatch(1)
            calls.set(0)
            arrivals.set(0)
            point = at
        }
    }

    /** Must be open: Spring proxies @Repository beans for exception translation. */
    open class GatedStaff(jdbc: JdbcTemplate, mapper: JsonMapper, private val gate: Gate) : StaffJdbcRepository(jdbc, mapper) {
        override fun lockStaff(wardId: UUID, nurseId: UUID) = run {
            if (gate.point == "profile" && gate.arrivals.incrementAndGet() == 2) gate.secondArrived.countDown()
            super.lockStaff(wardId, nurseId)
        }

        override fun confirmProfile(nurseId: UUID, careerStartedOn: String, rank: String, actor: String, now: Instant) {
            super.confirmProfile(nurseId, careerStartedOn, rank, actor, now)
            if (gate.point == "profile" && gate.calls.incrementAndGet() == 1) {
                gate.firstHolding.countDown()
                gate.release.await(10, TimeUnit.SECONDS)
            }
        }
    }

    open class GatedWards(jdbc: JdbcTemplate, private val gate: Gate) : WardJdbcRepository(jdbc) {
        override fun lockWard(wardId: UUID): Boolean {
            if (gate.point != "ward") return super.lockWard(wardId)
            return when (gate.calls.incrementAndGet()) {
                1 -> super.lockWard(wardId).also {
                    gate.firstHolding.countDown()
                    gate.release.await(10, TimeUnit.SECONDS)
                }
                2 -> {
                    gate.secondArrived.countDown()
                    super.lockWard(wardId)
                }
                else -> super.lockWard(wardId)
            }
        }
    }

    @TestConfiguration
    class Gates {
        @Bean fun gate() = Gate()

        @Bean @Primary
        fun gatedStaff(jdbc: JdbcTemplate, mapper: JsonMapper, gate: Gate) = GatedStaff(jdbc, mapper, gate)

        @Bean @Primary
        fun gatedWards(jdbc: JdbcTemplate, gate: Gate) = GatedWards(jdbc, gate)
    }

    @BeforeEach
    fun setup() {
        wardId = insertWard("동시성 병동")
        val (a, aToken) = userToken("가상 관리자 1")
        val (b, bToken) = userToken("가상 관리자 2")
        grant(wardId, a, "WARD_ADMIN")
        grant(wardId, b, "WARD_ADMIN")
        admin = aToken
        admin2 = bToken
    }

    @AfterEach
    fun open() {
        gate.point = ""
        gate.release.countDown()
    }

    private fun base() = "/api/v1/wards/$wardId"

    private fun async(call: () -> HttpTestClient.Response) = CompletableFuture.supplyAsync(call)

    @Test
    fun `concurrent career edits are recorded as a chain of real previous values`() {
        val nurse = http.post("${base()}/staff", mapOf("displayName" to "가상 간호사"), admin).json!!.get("nurseId").asString()
        gate.arm("profile")

        val first = async { http.put("${base()}/staff/$nurse/profile", mapOf("careerStartedOn" to "2020-03", "rank" to "과장"), admin) }
        assertTrue(gate.firstHolding.await(10, TimeUnit.SECONDS), "first edit should hold the nurse row")
        val second = async { http.put("${base()}/staff/$nurse/profile", mapOf("careerStartedOn" to "2018-01", "rank" to "팀장"), admin2) }
        assertTrue(gate.secondArrived.await(10, TimeUnit.SECONDS), "second edit should reach the nurse row lock")
        gate.release.countDown()

        assertEquals(200, first.get(10, TimeUnit.SECONDS).status)
        assertEquals(200, second.get(10, TimeUnit.SECONDS).status)
        val history = http.get("${base()}/staff/$nurse/adjustments", admin).json!!.get("items").toList()
            .filter { it.get("kind").asString() == "PROFILE_SET_BY_ADMIN" }
        // Newest first: O -> A, then A -> B. The second edit must not claim it replaced O.
        assertEquals("2020-03 / 과장", history[0].get("before").asString())
        assertEquals("2018-01 / 팀장", history[0].get("after").asString())
        assertTrue(history[1].get("before").isNull)
        assertEquals("2020-03 / 과장", history[1].get("after").asString())
    }

    @Test
    fun `a staff update waits for the ward lock and acts on the state committed before it`() {
        val team = http.post("${base()}/teams", mapOf("name" to "A팀"), admin).json!!.get("teamId").asString()
        val other = http.post("${base()}/staff", mapOf("displayName" to "가상 동료", "teamId" to team), admin).json!!.get("nurseId").asString()
        val nurse = http.post("${base()}/staff", mapOf("displayName" to "가상 간호사", "teamId" to team, "active" to false), admin)
            .json!!.get("nurseId").asString()
        gate.arm("ward")

        // The activation holds the ward lock; a deactivation of the same nurse (no change on its stale read)
        // must still queue on the lock instead of writing on the side.
        val activate = async { http.put("${base()}/staff/$nurse", mapOf("displayName" to "가상 간호사", "active" to true), admin) }
        assertTrue(gate.firstHolding.await(10, TimeUnit.SECONDS), "activation should hold the ward lock")
        val deactivate = async { http.put("${base()}/staff/$nurse", mapOf("displayName" to "가상 간호사", "active" to false), admin2) }
        assertTrue(gate.secondArrived.await(10, TimeUnit.SECONDS), "deactivation should queue on the ward lock")
        gate.release.countDown()
        assertEquals(200, activate.get(10, TimeUnit.SECONDS).status)
        assertEquals(200, deactivate.get(10, TimeUnit.SECONDS).status)
        gate.point = ""

        // Ordering the team now and reactivating later never yields a shared position.
        assertEquals(200, http.put("${base()}/teams/$team/order", mapOf("nurseIds" to listOf(other)), admin).status)
        http.put("${base()}/staff/$nurse", mapOf("displayName" to "가상 간호사", "active" to true), admin)
        val orders = http.get("${base()}/staff", admin).json!!.get("items").toList().map { it.get("teamOrder") }
        assertEquals(1, orders.count { !it.isNull })
        assertEquals(1, orders.first { !it.isNull }.asInt())
    }
}
