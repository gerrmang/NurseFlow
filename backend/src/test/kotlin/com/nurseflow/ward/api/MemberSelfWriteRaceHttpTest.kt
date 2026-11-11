package com.nurseflow.ward.api

import com.nurseflow.staff.persistence.repository.StaffJdbcRepository
import com.nurseflow.support.ApiTestBase
import com.nurseflow.support.HttpTestClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

/**
 * Forces the ordering a member's own write must survive: the member's deactivation has updated the linked nurse
 * row and not yet committed when the member's own profile request starts. Test-only subclass, no production hook:
 * the deactivation pauses right after deactivating the nurse row until the own request is blocked in the database.
 */
@Import(MemberSelfWriteRaceHttpTest.Gates::class)
class MemberSelfWriteRaceHttpTest : ApiTestBase() {
    @Autowired lateinit var gate: Gate

    class Gate {
        @Volatile var enabled = false
        @Volatile var deactivated = CountDownLatch(1)
        @Volatile var release = CountDownLatch(1)

        fun arm() {
            deactivated = CountDownLatch(1)
            release = CountDownLatch(1)
            enabled = true
        }
    }

    /** Must be open: Spring proxies @Repository beans for exception translation. */
    open class GatedStaff(jdbc: JdbcTemplate, mapper: JsonMapper, private val gate: Gate) : StaffJdbcRepository(jdbc, mapper) {
        override fun setStaffActive(nurseId: UUID, active: Boolean) {
            super.setStaffActive(nurseId, active)
            if (gate.enabled && !active) {
                gate.deactivated.countDown()
                gate.release.await(10, TimeUnit.SECONDS)
            }
        }
    }

    @TestConfiguration
    class Gates {
        @Bean fun gate() = Gate()

        @Bean @Primary
        fun gatedStaff(jdbc: JdbcTemplate, mapper: JsonMapper, gate: Gate) = GatedStaff(jdbc, mapper, gate)
    }

    @AfterEach
    fun open() {
        gate.enabled = false
        gate.release.countDown()
    }

    @Test
    fun `a member deactivated while their own profile write waits cannot finish it`() {
        val wardId = insertWard("가상 병동")
        val (admin, adminToken) = userToken("가상 관리자")
        val (member, memberToken) = userToken("가상 간호사")
        grant(wardId, admin, "WARD_ADMIN")
        grant(wardId, member, "WARD_MEMBER")
        val nurseId = insertNurse(wardId, subject = member.toString())
        gate.arm()

        val deactivate = CompletableFuture.supplyAsync {
            http.send("PATCH", "/api/v1/wards/$wardId/memberships/$member", mapOf("active" to false), adminToken, emptyMap())
        }
        assertTrue(gate.deactivated.await(10, TimeUnit.SECONDS), "the deactivation should hold the nurse row")
        val own = CompletableFuture.supplyAsync {
            http.put("/api/v1/wards/$wardId/my-profile", mapOf("hospitalJoinedOn" to "2020-03"), memberToken)
        }
        assertTrue(awaitBlockedSession(), "the own write should be blocked behind the deactivation")
        assertFalse(own.isDone)
        gate.release.countDown()

        val first: HttpTestClient.Response = deactivate.get(10, TimeUnit.SECONDS)
        val second: HttpTestClient.Response = own.get(10, TimeUnit.SECONDS)
        assertEquals(200, first.status, first.body)
        assertEquals(404, second.status, second.body)
        assertEquals("WARD_NOT_FOUND", second.code)
        assertEquals(null, jdbc.queryForObject(
            "SELECT hospital_joined_on FROM nurses WHERE nurse_id=?", String::class.java, nurseId))
    }

    /** The deactivation pauses in Java, not in the database, so the only blocked session is the own write. */
    private fun awaitBlockedSession(): Boolean {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (System.nanoTime() < deadline) {
            val blocked = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.SESSIONS WHERE BLOCKER_ID IS NOT NULL", Int::class.java,
            )!!
            if (blocked > 0) return true
            Thread.sleep(10)
        }
        return false
    }
}
