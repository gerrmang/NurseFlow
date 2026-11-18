package com.nurseflow.roster.api

import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
import java.util.concurrent.atomic.AtomicBoolean

/** Forces a mode update between the GET's demoMode read and its eligibility row read. */
@Import(RosterEligibilityRaceHttpTest.Gates::class)
class RosterEligibilityRaceHttpTest : ApiTestBase() {
    @Autowired lateinit var gate: Gate

    class Gate {
        val armed = AtomicBoolean(false)
        var read = CountDownLatch(1)
        var release = CountDownLatch(1)
    }

    open class GatedRoster(jdbc: JdbcTemplate, mapper: JsonMapper, private val gate: Gate) :
        RosterJdbcRepository(jdbc, mapper) {
        override fun demoMode(wardId: UUID): Boolean? {
            val mode = super.demoMode(wardId)
            if (gate.armed.compareAndSet(true, false)) {
                gate.read.countDown()
                gate.release.await(10, TimeUnit.SECONDS)
            }
            return mode
        }
    }

    @TestConfiguration
    class Gates {
        @Bean fun eligibilityGate() = Gate()
        @Bean @Primary fun gatedRoster(jdbc: JdbcTemplate, mapper: JsonMapper, gate: Gate) = GatedRoster(jdbc, mapper, gate)
    }

    @Test
    fun `a concurrent mode change cannot contradict one eligibility response`() {
        val ward = insertWard()
        val (admin, token) = userToken("가상 관리자")
        grant(ward, admin, "WARD_ADMIN")
        insertNurse(ward, "미가입 가상 간호사")
        val path = "/api/v1/wards/$ward/roster-eligibility"
        gate.read = CountDownLatch(1)
        gate.release = CountDownLatch(1)
        gate.armed.set(true)

        try {
            val oldSnapshot = CompletableFuture.supplyAsync { http.get(path, token) }
            assertEquals(true, gate.read.await(10, TimeUnit.SECONDS))
            val changed = http.put(path, mapOf("demoMode" to true), token, mapOf("If-Match" to "\"policy-0\""))
            assertEquals(200, changed.status, changed.body)
            gate.release.countDown()

            val response = oldSnapshot.get(10, TimeUnit.SECONDS)
            assertEquals(200, response.status, response.body)
            assertEquals("\"policy-0\"", response.header("ETag"))
            assertFalse(response.json!!.get("demoMode").asBoolean())
            val item = response.json.get("items").single()
            assertFalse(item.get("eligible").asBoolean())
            assertEquals("DEMO_MODE_DISABLED", item.get("reason").asString())
        } finally {
            gate.armed.set(false)
            gate.release.countDown()
        }
    }
}
