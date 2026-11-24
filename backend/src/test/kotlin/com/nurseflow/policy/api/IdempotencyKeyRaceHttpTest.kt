package com.nurseflow.policy.api

import com.nurseflow.common.idempotency.MutationReplayRepository
import com.nurseflow.policy.application.model.ShiftTypeCommand
import com.nurseflow.policy.application.service.ShiftTypeService
import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.support.ApiTestBase
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
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Forces two creates in different wards with the same Idempotency-Key past their replay lookups before either
 * commits: each locks only its own ward, so both reach the replay insert and the second loses on the global key.
 * Test-only subclass, no production hook: the second insert signals before it runs. Fictional data only.
 */
@Import(IdempotencyKeyRaceHttpTest.Gates::class)
class IdempotencyKeyRaceHttpTest : ApiTestBase() {
    @Autowired lateinit var gate: Gate
    @Autowired lateinit var shiftTypes: ShiftTypeService
    @Autowired lateinit var transactions: TransactionTemplate

    class Gate {
        @Volatile var enabled = false
        @Volatile var second = CountDownLatch(1)
        @Volatile var calls = 0
    }

    /** Must be open: Spring proxies @Repository beans for exception translation. */
    open class GatedReplays(jdbc: JdbcTemplate, mapper: JsonMapper, private val gate: Gate) : MutationReplayRepository(jdbc, mapper) {
        override fun recordReplay(key: UUID, operation: String, requestHash: String, response: Any) {
            if (gate.enabled && ++gate.calls == 2) gate.second.countDown()
            super.recordReplay(key, operation, requestHash, response)
        }
    }

    @TestConfiguration
    class Gates {
        @Bean fun gate() = Gate()

        @Bean @Primary
        fun gatedReplays(jdbc: JdbcTemplate, mapper: JsonMapper, gate: Gate) = GatedReplays(jdbc, mapper, gate)
    }

    @AfterEach
    fun open() {
        gate.enabled = false
    }

    @Test
    fun `the same key used in two wards at once is a conflict, not a server error`() {
        val (adminId, _) = userToken("가상 수간호사")
        val (otherAdminId, otherAdmin) = userToken("다른 수간호사")
        val ward = insertWard("근무 설정 병동")
        val otherWard = insertWard("다른 병동")
        grant(ward, adminId, "WARD_ADMIN")
        grant(otherWard, otherAdminId, "WARD_ADMIN")
        val key = UUID.randomUUID()
        val recorded = CountDownLatch(1)
        gate.calls = 0
        gate.second = CountDownLatch(1)
        gate.enabled = true

        // The first create writes its replay row and stays uncommitted until the second reaches its replay insert.
        val first = CompletableFuture.supplyAsync {
            transactions.execute {
                shiftTypes.create(ward, ShiftTypeCommand("N", "나이트", ShiftKind.NIGHT, LocalTime.of(22, 0), LocalTime.of(7, 0), true,
                    null, null, null, null), ShiftTypeService.policyEtag(0), key, adminId.toString()).also {
                    recorded.countDown()
                    gate.second.await(10, TimeUnit.SECONDS)
                }
            }!!
        }
        assertTrue(recorded.await(10, TimeUnit.SECONDS))
        val second = CompletableFuture.supplyAsync {
            http.post("/api/v1/wards/$otherWard/shift-types", mapOf(
                "code" to "N", "displayName" to "나이트", "kind" to "NIGHT",
                "startTime" to "22:00", "endTime" to "07:00", "spansMidnight" to true,
            ), otherAdmin, mapOf("Idempotency-Key" to key.toString(), "If-Match" to ShiftTypeService.policyEtag(0)))
        }

        assertEquals("N", first.get(10, TimeUnit.SECONDS).shiftType.code)
        val conflict = second.get(10, TimeUnit.SECONDS)
        assertEquals(409, conflict.status, conflict.body)
        assertEquals("IDEMPOTENCY_KEY_REUSED", conflict.code)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM shift_types WHERE ward_id=?", Int::class.java, otherWard))
    }
}
