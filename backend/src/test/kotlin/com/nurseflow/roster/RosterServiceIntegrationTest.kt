package com.nurseflow.roster

import com.nurseflow.common.error.ApiException
import com.nurseflow.roster.api.dto.request.*
import com.nurseflow.roster.api.dto.response.*
import com.nurseflow.roster.application.generation.GenerationCoordinator
import com.nurseflow.roster.application.service.RosterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:roster_integration;DB_CLOSE_DELAY=-1",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.invalid",
    "spring.security.oauth2.resourceserver.jwt.audiences=nurseflow-api",
])
class RosterServiceIntegrationTest @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val service: RosterService,
    private val generation: GenerationCoordinator,
) {
    private lateinit var wardId: UUID
    private lateinit var nurseId: UUID
    private lateinit var dayTypeId: UUID
    private lateinit var nightTypeId: UUID
    private val admin = "test-admin"

    @BeforeEach
    fun setup() {
        listOf("mutation_replays", "generation_jobs", "approved_absences", "roster_slots", "rosters",
            "shift_types", "nurses", "ward_memberships", "wards").forEach { jdbc.update("DELETE FROM $it") }
        wardId = UUID.randomUUID()
        nurseId = UUID.randomUUID()
        dayTypeId = UUID.randomUUID()
        nightTypeId = UUID.randomUUID()
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", wardId, "테스트 병동", "Asia/Seoul")
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,'WARD_ADMIN',TRUE)",
            wardId, admin)
        jdbc.update("INSERT INTO nurses(nurse_id,ward_id,subject,display_name,qualifications,active) VALUES(?,?,?,?,?,TRUE)",
            nurseId, wardId, "test-nurse", "간호사 1", "[]")
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'DAY','데이','DAY','07:00:00','15:00:00',FALSE)""", dayTypeId, wardId)
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'NIGHT','나이트','NIGHT','19:00:00','07:00:00',TRUE)""", nightTypeId, wardId)
    }

    @Test
    fun `confirm rechecks next-month day against newly published night`() {
        val october = newRoster("2026-10", LocalDate.parse("2026-10-31"), nightTypeId)
        val november = newRoster("2026-11", LocalDate.parse("2026-11-01"), dayTypeId)
        val octoberAssigned = assign(october, nurseId)
        val novemberAssigned = assign(november, nurseId)

        service.confirm(october.rosterId, tag(octoberAssigned), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        val conflict = assertThrows(ApiException::class.java) {
            service.confirm(november.rosterId, tag(novemberAssigned), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        }
        assertEquals("RULE_VIOLATION", conflict.code)
        assertTrue(conflict.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "REST_AFTER_NIGHT" })
        assertEquals("DRAFT", service.getRoster(november.rosterId, admin).status)
        assertThrows(ApiException::class.java) { service.publishedWard(wardId, "2026-11", admin) }
    }

    @Test
    fun `concurrent adjacent-month confirms cannot both publish conflicting shifts`() {
        val october = assign(newRoster("2026-10", LocalDate.parse("2026-10-31"), nightTypeId), nurseId)
        val november = assign(newRoster("2026-11", LocalDate.parse("2026-11-01"), dayTypeId), nurseId)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val results = listOf(october, november).map { roster ->
                executor.submit<Boolean> {
                    start.await()
                    try {
                        service.confirm(roster.rosterId, tag(roster), UUID.randomUUID(), ConfirmRosterRequest(), admin)
                        true
                    } catch (ex: ApiException) {
                        assertEquals("RULE_VIOLATION", ex.code)
                        false
                    }
                }
            }
            start.countDown()
            assertEquals(1, results.count { it.get(10, TimeUnit.SECONDS) })
            assertEquals(1, listOf(october, november).count {
                service.getRoster(it.rosterId, admin).status == "PUBLISHED"
            })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `confirm rejects draft absence crossing into published prior-month shift`() {
        val november = newRoster("2026-11", LocalDate.parse("2026-11-02"), dayTypeId)
        val absence = ApprovedAbsenceInput(nurseId, "APPROVED_HALF_DAY",
            Instant.parse("2026-10-31T20:00:00Z"), Instant.parse("2026-11-01T03:00:00Z"))
        val added = service.addAbsence(november.rosterId, tag(november), UUID.randomUUID(), absence, admin)
        val october = newRoster("2026-10", LocalDate.parse("2026-10-31"), nightTypeId)
        val octoberAssigned = assign(october, nurseId)
        service.confirm(october.rosterId, tag(octoberAssigned), UUID.randomUUID(), ConfirmRosterRequest(), admin)

        val conflict = assertThrows(ApiException::class.java) {
            service.confirm(november.rosterId, "\"${service.etag(november.rosterId, added.rosterRevision)}\"",
                UUID.randomUUID(), ConfirmRosterRequest(1), admin)
        }
        assertTrue(conflict.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "APPROVED_ABSENCE_PUBLISHED_OVERLAP" })
        assertEquals("DRAFT", service.getRoster(november.rosterId, admin).status)
    }

    @Test
    fun `generation respects approved leave and publishes only after explicit confirm`() {
        val secondNurse = UUID.randomUUID()
        jdbc.update("INSERT INTO nurses(nurse_id,ward_id,subject,display_name,qualifications,active) VALUES(?,?,?,?,?,TRUE)",
            secondNurse, wardId, "test-nurse-2", "간호사 2", "[]")
        val draft = service.createRoster(wardId, CreateRosterRequest("2026-11"), admin, UUID.randomUUID())
        val requirements = service.replaceRequirements(draft.rosterId, tag(draft), ReplaceRequirementsRequest(listOf(
            StaffingRequirement(LocalDate.parse("2026-11-01"), dayTypeId, 2, emptyList()),
        )), admin)
        val absent = service.addAbsence(draft.rosterId,
            "\"${service.etag(draft.rosterId, requirements.revision)}\"", UUID.randomUUID(),
            ApprovedAbsenceInput(nurseId, "APPROVED_LEAVE", Instant.parse("2026-10-31T22:00:00Z"),
                Instant.parse("2026-11-01T06:00:00Z")), admin)

        val job = generation.start(draft.rosterId,
            "\"${service.etag(draft.rosterId, absent.rosterRevision)}\"", UUID.randomUUID(),
            StartGenerationRequest(preserveLockedAssignments = true, timeLimitSeconds = 10), admin)
        var finished = service.getGenerationJob(job.jobId, admin)
        repeat(100) {
            if (finished.status !in setOf("QUEUED", "RUNNING")) return@repeat
            Thread.sleep(50)
            finished = service.getGenerationJob(job.jobId, admin)
        }
        assertEquals("PARTIAL", finished.status)
        val generated = service.getRoster(draft.rosterId, admin)
        assertEquals(1, generated.staffing.assigned)
        assertEquals(1, generated.staffing.unfilled)
        assertFalse(generated.slots.any { it.assignment?.nurseId == nurseId })
        assertThrows(ApiException::class.java) { service.publishedWard(wardId, "2026-11", admin) }

        service.confirm(draft.rosterId, tag(generated), UUID.randomUUID(), ConfirmRosterRequest(1), admin)
        val published = service.publishedWard(wardId, "2026-11", admin)
        assertEquals(1, published.staffing.unfilled)
        assertEquals(1, published.staffing.assigned)
    }

    @Test
    fun `queued generation can be cancelled without changing draft revision`() {
        val draft = newRoster("2026-11", LocalDate.parse("2026-11-01"), dayTypeId)
        val queued = service.startGeneration(draft.rosterId, tag(draft), UUID.randomUUID(),
            StartGenerationRequest(preserveLockedAssignments = true), admin)
        val cancelled = service.cancelGeneration(queued.jobId, UUID.randomUUID(), admin)
        assertEquals("CANCELLED", cancelled.status)
        assertFalse(service.markGenerationRunning(queued.jobId))
        assertEquals(draft.revision, service.getRoster(draft.rosterId, admin).revision)
    }

    @Test
    fun `same-month replacement draft retains approved leave before new assignments`() {
        val original = assign(newRoster("2026-11", LocalDate.parse("2026-11-02"), dayTypeId), nurseId)
        val input = ApprovedAbsenceInput(nurseId, "APPROVED_LEAVE",
            Instant.parse("2026-10-31T22:00:00Z"), Instant.parse("2026-11-01T06:00:00Z"))
        val added = service.addAbsence(original.rosterId, tag(original), UUID.randomUUID(), input, admin)
        service.confirm(original.rosterId,
            "\"${service.etag(original.rosterId, added.rosterRevision)}\"", UUID.randomUUID(),
            ConfirmRosterRequest(), admin)

        val replacement = service.createRoster(wardId, CreateRosterRequest("2026-11"), admin, UUID.randomUUID())
        val emptyConfirmation = assertThrows(ApiException::class.java) {
            service.confirm(replacement.rosterId, tag(replacement), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        }
        assertEquals("ROSTER_REQUIREMENTS_REQUIRED", emptyConfirmation.code)
        assertEquals("PUBLISHED", service.getRoster(original.rosterId, admin).status)
        assertEquals(nurseId, service.publishedWard(wardId, "2026-11", admin).slots.single().assignment?.nurseId)
        val copied = service.listAbsences(replacement.rosterId, admin).single()
        assertEquals(input.nurseId, copied.nurseId)
        assertEquals(input.kind, copied.kind)
        assertEquals(input.startsAt, copied.startsAt)
        assertEquals(input.endsAt, copied.endsAt)
        assertTrue(copied.absenceId != added.absence.absenceId)

        val requirements = service.replaceRequirements(replacement.rosterId, tag(replacement),
            ReplaceRequirementsRequest(listOf(
                StaffingRequirement(LocalDate.parse("2026-11-01"), dayTypeId, 1, emptyList()),
            )), admin)
        val slot = service.getRoster(replacement.rosterId, admin).slots.single()
        val conflict = assertThrows(ApiException::class.java) {
            service.assign(replacement.rosterId, slot.slotId,
                "\"${service.etag(replacement.rosterId, requirements.revision)}\"",
                UUID.randomUUID(), AssignNurseRequest(nurseId), admin)
        }
        assertEquals("RULE_VIOLATION", conflict.code)
        assertTrue(conflict.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "APPROVED_LEAVE_OVERLAP" })
        assertEquals("PUBLISHED", service.getRoster(original.rosterId, admin).status)
        assertEquals(1, service.listAbsences(original.rosterId, admin).size)
    }

    private fun newRoster(month: String, date: LocalDate, shiftTypeId: UUID): RosterView {
        val draft = service.createRoster(wardId, CreateRosterRequest(month), admin, UUID.randomUUID())
        service.replaceRequirements(draft.rosterId, tag(draft), ReplaceRequirementsRequest(listOf(
            StaffingRequirement(date, shiftTypeId, 1, emptyList()),
        )), admin)
        return service.getRoster(draft.rosterId, admin)
    }

    private fun assign(roster: RosterView, nurse: UUID): RosterView = service.assign(
        roster.rosterId, roster.slots.single().slotId, tag(roster), UUID.randomUUID(), AssignNurseRequest(nurse), admin,
    )

    private fun tag(roster: RosterView): String = "\"${service.etag(roster.rosterId, roster.revision)}\""
}
