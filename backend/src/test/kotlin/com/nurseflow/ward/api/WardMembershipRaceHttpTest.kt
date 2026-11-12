package com.nurseflow.ward.api

import com.nurseflow.support.ApiTestBase
import com.nurseflow.support.HttpTestClient
import com.nurseflow.ward.persistence.repository.WardJdbcRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.util.concurrent.atomic.AtomicInteger

/**
 * Forces the ordering the post-lock admin re-check exists for: a request that passed the first admin check
 * waits on the ward lock while another request removes the caller's admin role. Test-only subclass, no
 * production hook: the first lock holder pauses after acquiring the lock until the second request arrives.
 */
@Import(WardMembershipRaceHttpTest.Gates::class)
class WardMembershipRaceHttpTest : ApiTestBase() {
    @Autowired lateinit var gate: Gate

    class Gate {
        @Volatile var enabled = false
        @Volatile var firstLocked = CountDownLatch(1)
        @Volatile var secondArrived = CountDownLatch(1)
        @Volatile var release = CountDownLatch(1)
        val locks = AtomicInteger()

        fun arm() {
            firstLocked = CountDownLatch(1)
            secondArrived = CountDownLatch(1)
            release = CountDownLatch(1)
            locks.set(0)
            enabled = true
        }
    }

    /** Must be open: Spring proxies @Repository beans for exception translation. */
    open class GatedWards(jdbc: JdbcTemplate, private val gate: Gate) : WardJdbcRepository(jdbc) {
        override fun lockWard(wardId: UUID): Boolean {
            if (!gate.enabled) return super.lockWard(wardId)
            return when (gate.locks.incrementAndGet()) {
                1 -> super.lockWard(wardId).also {
                    gate.firstLocked.countDown()
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
        fun gatedWards(jdbc: JdbcTemplate, gate: Gate) = GatedWards(jdbc, gate)
    }

    @AfterEach
    fun open() {
        gate.enabled = false
        gate.release.countDown()
    }

    private fun patch(wardId: UUID, userId: UUID, body: Map<String, Any>, token: String): CompletableFuture<HttpTestClient.Response> =
        CompletableFuture.supplyAsync { http.send("PATCH", "/api/v1/wards/$wardId/memberships/$userId", body, token, emptyMap()) }

    @Test
    fun `an admin whose role is revoked while waiting for the lock cannot finish the change`() {
        val wardId = insertWard("가상 병동")
        val (a, aToken) = userToken("가상 관리자 A")
        val (b, bToken) = userToken("가상 관리자 B")
        val (c, _) = userToken("가상 구성원 C")
        grant(wardId, a, "WARD_ADMIN")
        grant(wardId, b, "WARD_ADMIN")
        grant(wardId, b, "WARD_MEMBER")
        grant(wardId, c, "WARD_MEMBER")
        gate.arm()

        // A takes the lock and revokes B's admin role; B already passed its first admin check and queues.
        val revokeB = patch(wardId, b, mapOf("admin" to false), aToken)
        assertTrue(gate.firstLocked.await(10, TimeUnit.SECONDS), "A should hold the ward lock")
        val bActs = patch(wardId, c, mapOf("active" to false), bToken)
        assertTrue(gate.secondArrived.await(10, TimeUnit.SECONDS), "B should reach the ward lock")
        gate.release.countDown()

        val first = revokeB.get(10, TimeUnit.SECONDS)
        val second = bActs.get(10, TimeUnit.SECONDS)
        assertEquals(200, first.status, first.body)
        assertEquals(403, second.status, second.body)
        assertEquals("WARD_ADMIN_REQUIRED", second.code)
        assertEquals(true, jdbc.queryForObject(
            "SELECT active FROM ward_memberships WHERE ward_id=? AND subject=?", Boolean::class.java, wardId, c.toString()))
    }

    @Test
    fun `an admin revoked while a staff change waits for the lock cannot finish it`() {
        val wardId = insertWard("가상 병동")
        val (a, aToken) = userToken("가상 관리자 A")
        val (b, bToken) = userToken("가상 관리자 B")
        grant(wardId, a, "WARD_ADMIN")
        grant(wardId, b, "WARD_ADMIN")
        grant(wardId, b, "WARD_MEMBER")
        gate.arm()

        val revokeB = patch(wardId, b, mapOf("admin" to false), aToken)
        assertTrue(gate.firstLocked.await(10, TimeUnit.SECONDS), "A should hold the ward lock")
        val bCreates = CompletableFuture.supplyAsync { http.post("/api/v1/wards/$wardId/teams", mapOf("name" to "가상 팀"), bToken) }
        assertTrue(gate.secondArrived.await(10, TimeUnit.SECONDS), "B's staff change should reach the ward lock")
        gate.release.countDown()

        assertEquals(200, revokeB.get(10, TimeUnit.SECONDS).status)
        val second = bCreates.get(10, TimeUnit.SECONDS)
        assertEquals(403, second.status, second.body)
        assertEquals("WARD_ADMIN_REQUIRED", second.code)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM ward_teams WHERE ward_id=?", Int::class.java, wardId))
    }

    @Test
    fun `an admin revoked while a shift type change waits for the lock cannot finish it`() {
        val wardId = insertWard("가상 병동")
        val (a, aToken) = userToken("가상 관리자 A")
        val (b, bToken) = userToken("가상 관리자 B")
        grant(wardId, a, "WARD_ADMIN")
        grant(wardId, b, "WARD_ADMIN")
        grant(wardId, b, "WARD_MEMBER")
        gate.arm()

        val revokeB = patch(wardId, b, mapOf("admin" to false), aToken)
        assertTrue(gate.firstLocked.await(10, TimeUnit.SECONDS), "A should hold the ward lock")
        val bCreates = CompletableFuture.supplyAsync {
            http.post("/api/v1/wards/$wardId/shift-types", mapOf(
                "code" to "N", "displayName" to "나이트", "kind" to "NIGHT",
                "startTime" to "22:00", "endTime" to "07:00", "spansMidnight" to true,
            ), bToken, mapOf("Idempotency-Key" to UUID.randomUUID().toString()))
        }
        assertTrue(gate.secondArrived.await(10, TimeUnit.SECONDS), "B's shift type change should reach the ward lock")
        gate.release.countDown()

        assertEquals(200, revokeB.get(10, TimeUnit.SECONDS).status)
        val second = bCreates.get(10, TimeUnit.SECONDS)
        assertEquals(403, second.status, second.body)
        assertEquals("WARD_ADMIN_REQUIRED", second.code)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM shift_types WHERE ward_id=?", Int::class.java, wardId))
    }
}
