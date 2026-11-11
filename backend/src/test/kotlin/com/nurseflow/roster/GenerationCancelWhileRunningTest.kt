package com.nurseflow.roster

import com.nurseflow.roster.application.model.CreateRosterRequest
import com.nurseflow.roster.application.model.ReplaceRequirementsRequest
import com.nurseflow.roster.application.model.StaffingRequirement
import com.nurseflow.roster.application.model.StartGenerationRequest
import com.nurseflow.roster.application.service.RosterService
import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import org.junit.jupiter.api.AfterEach
import com.nurseflow.common.error.BusinessException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * A running generation holds the ward lock until it saves its result. Cancelling it must still record the
 * signal right away, so the loop stops and the draft keeps its revision (PR #41 review regression).
 * The test-only repository pauses the loop at its first cancellation check. Fictional data only.
 */
@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:generation_cancel;DB_CLOSE_DELAY=-1"])
@Import(GenerationCancelWhileRunningTest.Gates::class)
class GenerationCancelWhileRunningTest @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val service: RosterService,
    private val gate: Gate,
) {
    private val admin = "test-admin"
    private lateinit var wardId: UUID
    private lateinit var dayTypeId: UUID

    class Gate {
        @Volatile var armed = false
        @Volatile var armedAtDecision = false
        @Volatile var paused = CountDownLatch(1)
        @Volatile var release = CountDownLatch(1)
    }

    /** Must be open: Spring proxies @Repository beans for exception translation. */
    open class GatedRoster(jdbc: JdbcTemplate, mapper: JsonMapper, private val gate: Gate) : RosterJdbcRepository(jdbc, mapper) {
        override fun generationCancelled(jobId: UUID): Boolean {
            if (gate.armed) {
                gate.armed = false
                gate.paused.countDown()
                gate.release.await(10, TimeUnit.SECONDS)
            }
            return super.generationCancelled(jobId)
        }

        /** The generation's own lock before applying its result is the first call once armed. */
        override fun lockGenerationJob(jobId: UUID) {
            super.lockGenerationJob(jobId)
            if (gate.armedAtDecision) {
                gate.armedAtDecision = false
                gate.paused.countDown()
                gate.release.await(10, TimeUnit.SECONDS)
            }
        }
    }

    @TestConfiguration
    class Gates {
        @Bean fun gate() = Gate()

        @Bean @Primary
        fun gatedRoster(jdbc: JdbcTemplate, mapper: JsonMapper, gate: Gate) = GatedRoster(jdbc, mapper, gate)
    }

    @BeforeEach
    fun setup() {
        listOf("mutation_replays", "generation_jobs", "approved_absences", "roster_slots", "rosters",
            "shift_types", "nurses", "ward_memberships", "wards").forEach { jdbc.update("DELETE FROM $it") }
        wardId = UUID.randomUUID()
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", wardId, "가상 병동", "Asia/Seoul")
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,'WARD_ADMIN',TRUE)", wardId, admin)
        jdbc.update("INSERT INTO nurses(nurse_id,ward_id,subject,display_name,qualifications,active) VALUES(?,?,?,?,?,TRUE)",
            UUID.randomUUID(), wardId, "test-nurse", "가상 간호사", "[]")
        dayTypeId = UUID.randomUUID()
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'DAY','데이','DAY','07:00:00','15:00:00',FALSE)""", dayTypeId, wardId)
    }

    @AfterEach
    fun open() {
        gate.armed = false
        gate.armedAtDecision = false
        gate.release.countDown()
    }

    @Test
    fun `a running generation can be cancelled while it holds the ward lock`() {
        val created = service.createRoster(wardId, CreateRosterRequest("2026-11"), admin, UUID.randomUUID())
        service.replaceRequirements(created.rosterId, tag(created.rosterId, created.revision), ReplaceRequirementsRequest(listOf(
            StaffingRequirement(LocalDate.parse("2026-11-02"), dayTypeId, 1, emptyList()),
        )), admin)
        val draft = service.getRoster(created.rosterId, admin)
        val etag = tag(draft.rosterId, draft.revision)
        val job = service.startGeneration(draft.rosterId, etag, UUID.randomUUID(), StartGenerationRequest(preserveLockedAssignments = true), admin)
        assertTrue(service.markGenerationRunning(job.jobId))
        gate.paused = CountDownLatch(1)
        gate.release = CountDownLatch(1)
        gate.armed = true

        val running = CompletableFuture.runAsync { service.processGeneration(job.jobId) }
        assertTrue(gate.paused.await(10, TimeUnit.SECONDS), "generation should be inside its loop, holding the ward lock")
        // Must not wait for the ward lock: the generation keeps it until the gate opens.
        val cancel = CompletableFuture.supplyAsync { service.cancelGeneration(job.jobId, UUID.randomUUID(), admin) }
        val requested = cancel.get(5, TimeUnit.SECONDS)
        assertEquals("RUNNING", requested.status)
        gate.release.countDown()
        running.get(10, TimeUnit.SECONDS)

        assertEquals("CANCELLED", service.getGenerationJob(job.jobId, admin).status)
        assertEquals(draft.revision, service.getRoster(draft.rosterId, admin).revision)
    }

    @Test
    fun `a cancel arriving after the last loop check either wins or is refused, never answered RUNNING`() {
        val draft = draft()
        val job = service.startGeneration(draft.rosterId, tag(draft.rosterId, draft.revision), UUID.randomUUID(),
            StartGenerationRequest(preserveLockedAssignments = true), admin)
        assertTrue(service.markGenerationRunning(job.jobId))
        gate.paused = CountDownLatch(1)
        gate.release = CountDownLatch(1)
        gate.armedAtDecision = true

        val running = CompletableFuture.runAsync { service.processGeneration(job.jobId) }
        assertTrue(gate.paused.await(10, TimeUnit.SECONDS), "generation should hold the job row before applying its result")
        val cancel = CompletableFuture.supplyAsync {
            runCatching { service.cancelGeneration(job.jobId, UUID.randomUUID(), admin) }
        }
        Thread.sleep(300)
        assertFalse(cancel.isDone, "the cancel must wait for the generation's decision instead of reporting RUNNING")
        gate.release.countDown()
        running.get(10, TimeUnit.SECONDS)

        val refused = cancel.get(10, TimeUnit.SECONDS).exceptionOrNull() as BusinessException
        assertEquals("GENERATION_NOT_CANCELLABLE", refused.errorCode.name)
        val finished = service.getGenerationJob(job.jobId, admin)
        assertTrue(finished.status in setOf("COMPLETED", "PARTIAL"), finished.status)
        assertEquals(draft.revision + 1, service.getRoster(draft.rosterId, admin).revision)
    }

    private fun draft(): com.nurseflow.roster.application.model.RosterView {
        val created = service.createRoster(wardId, CreateRosterRequest("2026-11"), admin, UUID.randomUUID())
        service.replaceRequirements(created.rosterId, tag(created.rosterId, created.revision), ReplaceRequirementsRequest(listOf(
            StaffingRequirement(LocalDate.parse("2026-11-02"), dayTypeId, 1, emptyList()),
        )), admin)
        return service.getRoster(created.rosterId, admin)
    }

    private fun tag(rosterId: UUID, revision: Int) = "\"${service.etag(rosterId, revision)}\""
}
