package com.nurseflow.roster.persistence.repository

import com.nurseflow.common.error.ApiException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** 멱등성 replay 직렬화 확인용 값. API DTO에 의존하지 않도록 테스트 전용 타입을 쓴다. */
data class ReplayProbe(val label: String, val revision: Int)

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:roster_repository;DB_CLOSE_DELAY=-1",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.invalid",
    "spring.security.oauth2.resourceserver.jwt.audiences=nurseflow-api",
])
class RosterJdbcRepositoryTest @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val repo: RosterJdbcRepository,
    transactionManager: PlatformTransactionManager,
) {
    private val tx = TransactionTemplate(transactionManager)
    private lateinit var wardId: UUID
    private lateinit var nurseId: UUID

    @BeforeEach
    fun setup() {
        listOf("mutation_replays", "generation_jobs", "approved_absences", "roster_slots", "rosters",
            "shift_types", "nurses", "ward_memberships", "wards").forEach { jdbc.update("DELETE FROM $it") }
        wardId = UUID.randomUUID()
        nurseId = UUID.randomUUID()
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", wardId, "테스트 병동", "Asia/Seoul")
        jdbc.update("INSERT INTO nurses(nurse_id,ward_id,subject,display_name,qualifications,active) VALUES(?,?,?,?,?,TRUE)",
            nurseId, wardId, "test-nurse", "간호사 1", "[]")
    }

    @Test
    fun `roster insert, revision increment and publish round-trip through SQL`() {
        val rosterId = UUID.randomUUID()
        assertFalse(repo.draftExistsForMonth(wardId, "2026-11"))

        repo.insertRoster(rosterId, wardId, "2026-11")
        val draft = repo.findRoster(rosterId)!!
        assertEquals("DRAFT", draft.status)
        assertEquals(1, draft.revision)
        assertEquals("Asia/Seoul", draft.zone.id)
        assertNull(draft.publishedAt)
        assertTrue(repo.draftExistsForMonth(wardId, "2026-11"))
        assertEquals(listOf(rosterId), repo.listRosterIds(wardId, "2026-11"))
        assertNull(repo.activePublished(wardId, "2026-11"))

        repo.increment(rosterId)
        assertEquals(2, repo.findRoster(rosterId)!!.revision)

        val publishedAt = Instant.parse("2026-10-30T00:00:00Z")
        repo.publishRoster(rosterId, publishedAt, "test-admin")
        val published = repo.activePublished(wardId, "2026-11")!!
        assertEquals(rosterId, published.id)
        assertEquals("PUBLISHED", published.status)
        assertEquals(3, published.revision)
        assertEquals(publishedAt, published.publishedAt)
        assertEquals("test-admin", published.confirmedBy)
        assertFalse(repo.draftExistsForMonth(wardId, "2026-11"))

        repo.deactivatePublished(wardId, "2026-11")
        assertNull(repo.activePublished(wardId, "2026-11"))
        assertEquals(3, repo.findRoster(rosterId)!!.revision)
        assertNull(repo.findRoster(UUID.randomUUID()))
    }

    @Test
    fun `approved absences are stored and returned as persistence rows`() {
        val rosterId = UUID.randomUUID()
        repo.insertRoster(rosterId, wardId, "2026-11")
        val absenceId = UUID.randomUUID()
        val start = Instant.parse("2026-10-31T22:00:00Z")
        val end = Instant.parse("2026-11-01T06:00:00Z")

        repo.insertAbsence(absenceId, rosterId, nurseId, "APPROVED_LEAVE", start, end)
        val stored = repo.absences(rosterId).single()
        assertEquals(absenceId, stored.id)
        assertEquals(nurseId, stored.nurseId)
        assertEquals("APPROVED_LEAVE", stored.kind)
        assertEquals(start, stored.startsAt)
        assertEquals(end, stored.endsAt)

        val halfDayEnd = Instant.parse("2026-11-01T03:00:00Z")
        repo.updateAbsence(absenceId, rosterId, nurseId, "APPROVED_HALF_DAY", start, halfDayEnd)
        val updated = repo.absences(rosterId).single()
        assertEquals("APPROVED_HALF_DAY", updated.kind)
        assertEquals(halfDayEnd, updated.endsAt)

        assertFalse(repo.deleteAbsence(absenceId, UUID.randomUUID()))
        assertTrue(repo.deleteAbsence(absenceId, rosterId))
        assertTrue(repo.absences(rosterId).isEmpty())
    }

    @Test
    fun `idempotency replay returns stored response only for the same operation and request`() {
        val key = UUID.randomUUID()
        assertNull(repo.replay(key, "assign:roster", "hash-a", ReplayProbe::class.java))

        repo.recordReplay(key, "assign:roster", "hash-a", ReplayProbe("first", 2))
        assertEquals(ReplayProbe("first", 2), repo.replay(key, "assign:roster", "hash-a", ReplayProbe::class.java))

        val otherRequest = assertThrows(ApiException::class.java) {
            repo.replay(key, "assign:roster", "hash-b", ReplayProbe::class.java)
        }
        assertEquals(HttpStatus.CONFLICT, otherRequest.status)
        assertEquals("IDEMPOTENCY_KEY_REUSED", otherRequest.code)

        val otherOperation = assertThrows(ApiException::class.java) {
            repo.replay(key, "confirm:roster", "hash-a", ReplayProbe::class.java)
        }
        assertEquals("IDEMPOTENCY_KEY_REUSED", otherOperation.code)
        val stored: Int = jdbc.queryForObject("SELECT COUNT(*) FROM mutation_replays", Int::class.java)!!
        assertEquals(1, stored)
    }

    @Test
    fun `lockWard rejects unknown ward`() {
        val missing = assertThrows(ApiException::class.java) {
            tx.executeWithoutResult { repo.lockWard(UUID.randomUUID()) }
        }
        assertEquals(HttpStatus.NOT_FOUND, missing.status)
        assertEquals("WARD_NOT_FOUND", missing.code)
    }

    @Test
    fun `lockWard blocks a second transaction until the holder commits`() {
        val firstLocked = CountDownLatch(1)
        val firstReleasing = AtomicBoolean(false)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val holder = executor.submit<Unit> {
                tx.executeWithoutResult {
                    repo.lockWard(wardId)
                    firstLocked.countDown()
                    Thread.sleep(300)
                    firstReleasing.set(true)
                }
            }
            assertTrue(firstLocked.await(5, TimeUnit.SECONDS))
            val waiter = executor.submit<Boolean?> {
                tx.execute {
                    repo.lockWard(wardId)
                    firstReleasing.get()
                }
            }
            holder.get(10, TimeUnit.SECONDS)
            assertTrue(waiter.get(10, TimeUnit.SECONDS) == true, "second lockWard acquired before first transaction ended")
        } finally {
            executor.shutdownNow()
        }
    }
}
