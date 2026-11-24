package com.nurseflow.roster

import com.nurseflow.common.error.BusinessException
import com.nurseflow.roster.application.model.*
import com.nurseflow.roster.application.generation.GenerationCoordinator
import com.nurseflow.roster.application.service.RosterService
import com.nurseflow.roster.application.service.RosterEligibilityService
import com.nurseflow.policy.application.model.ShiftTypeCommand
import com.nurseflow.policy.application.service.ShiftTypeService
import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.domain.model.RestDayKind
import com.nurseflow.roster.domain.model.RosterRestDay
import com.nurseflow.staff.application.service.StaffService
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
])
class RosterServiceIntegrationTest @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val service: RosterService,
    private val generation: GenerationCoordinator,
    private val shiftTypes: ShiftTypeService,
    private val eligibility: RosterEligibilityService,
    private val staff: StaffService,
) {
    private lateinit var wardId: UUID
    private lateinit var nurseId: UUID
    private lateinit var dayTypeId: UUID
    private lateinit var nightTypeId: UUID
    private val admin = "test-admin"

    @Test
    fun `unlinked staff cannot be validated assigned or confirmed until demo mode is enabled`() {
        val fictional = UUID.randomUUID()
        jdbc.update("INSERT INTO nurses(nurse_id,ward_id,display_name,qualifications,active) VALUES(?,?,?,'[]',TRUE)",
            fictional, wardId, "가상 미가입 간호사")
        val draft = newRoster("2026-11", LocalDate.parse("2026-11-02"), dayTypeId)
        val slot = draft.slots.single().slotId

        val candidate = service.validate(draft.rosterId, tag(draft), ValidateAssignmentRequest(slot, fictional), admin)
        assertFalse(candidate.allowed)
        assertTrue(candidate.hardViolations.any { it.code == "NURSE_INACTIVE" })
        assertThrows(BusinessException::class.java) { assign(draft, fictional) }
        // A stored assignment from before the policy changed must also fail at publication.
        jdbc.update("UPDATE roster_slots SET nurse_id=? WHERE slot_id=?", fictional, slot)
        assertThrows(BusinessException::class.java) {
            service.confirm(draft.rosterId, tag(draft), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        }
        jdbc.update("UPDATE roster_slots SET nurse_id=NULL WHERE slot_id=?", slot)

        eligibility.setDemoMode(wardId, true, ShiftTypeService.policyEtag(0), admin)
        val refreshed = service.getRoster(draft.rosterId, admin)
        assertTrue(service.validate(refreshed.rosterId, tag(refreshed), ValidateAssignmentRequest(slot, fictional), admin).allowed)
        val assigned = assign(refreshed, fictional)
        service.confirm(assigned.rosterId, tag(assigned), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        assertEquals("PUBLISHED", service.getRoster(assigned.rosterId, admin).status)
    }

    @Test
    fun `disabling demo mode invalidates fictional assignments but preserves the published roster`() {
        val fictional = UUID.randomUUID()
        jdbc.update("INSERT INTO nurses(nurse_id,ward_id,display_name,qualifications,active) VALUES(?,?,?,'[]',TRUE)",
            fictional, wardId, "가상 미가입 간호사")
        val publishedDraft = assign(newRoster("2026-10", LocalDate.parse("2026-10-30"), dayTypeId), nurseId)
        service.confirm(publishedDraft.rosterId, tag(publishedDraft), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        val publishedBefore = service.publishedWard(wardId, "2026-10", admin)

        eligibility.setDemoMode(wardId, true, ShiftTypeService.policyEtag(0), admin)
        val assigned = assign(newRoster("2026-11", LocalDate.parse("2026-11-02"), dayTypeId), fictional)
        val disabled = eligibility.setDemoMode(wardId, false, ShiftTypeService.policyEtag(1), admin)

        assertFalse(disabled.demoMode)
        assertEquals(2, disabled.policyRevision)
        assertEquals("DEMO_MODE_DISABLED", disabled.items.single { it.nurseId == fictional }.reason)
        val changed = service.getRoster(assigned.rosterId, admin)
        assertEquals(assigned.revision + 1, changed.revision)
        assertTrue(changed.needsRevalidation)
        assertEquals(fictional, changed.slots.single().assignment?.nurseId)

        val stale = assertThrows(BusinessException::class.java) {
            service.confirm(assigned.rosterId, tag(assigned), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        }
        assertEquals("STALE_ROSTER_REVISION", stale.errorCode.name)
        val rejected = assertThrows(BusinessException::class.java) {
            service.confirm(changed.rosterId, tag(changed), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        }
        assertEquals("RULE_VIOLATION", rejected.errorCode.name)
        assertTrue(rejected.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "NURSE_INACTIVE" })
        assertEquals("DRAFT", service.getRoster(changed.rosterId, admin).status)
        assertEquals(publishedBefore, service.publishedWard(wardId, "2026-10", admin))
        assertThrows(BusinessException::class.java) { service.publishedWard(wardId, "2026-11", admin) }
    }

    @Test
    fun `team demand keeps its senior slot empty when only a junior is available`() {
        val team = staff.createTeam(wardId, "가상 A팀", null, admin).teamId
        val otherTeam = staff.createTeam(wardId, "가상 B팀", null, admin).teamId
        val date = LocalDate.parse("2026-11-30")
        val draft = service.createRoster(wardId, CreateRosterRequest("2026-11"), admin, UUID.randomUUID())
        fun requirements(vararg items: StaffingRequirement) =
            service.replaceRequirements(draft.rosterId, tag(draft), ReplaceRequirementsRequest(items.toList()), admin)
        val required = StaffingRequirement(date, nightTypeId, 2, emptyList(), team)

        assertEquals("TEAM_REQUIRED_FOR_DEMAND", assertThrows(BusinessException::class.java) {
            requirements(required.copy(teamId = null))
        }.errorCode.name)
        assertEquals("TEAM_NOT_FOUND", assertThrows(BusinessException::class.java) {
            requirements(required.copy(teamId = UUID.randomUUID()))
        }.errorCode.name)
        assertEquals("DUPLICATE_REQUIREMENT", assertThrows(BusinessException::class.java) {
            requirements(required, required)
        }.errorCode.name)
        val summary = requirements(required, required.copy(teamId = otherTeam, requiredCount = 0))
        assertEquals(1, summary.staffing.seniorUnfilled)
        assertEquals("TEAM_USED_IN_DRAFT", assertThrows(BusinessException::class.java) {
            staff.deleteTeam(wardId, team, admin)
        }.errorCode.name)
        staff.deleteTeam(wardId, otherTeam, admin)
        var current = service.getRoster(draft.rosterId, admin)
        val senior = current.slots.single { it.seniorRequired }
        assertTrue(current.slots.all { it.teamId == team })
        assertTrue(service.validate(current.rosterId, tag(current), ValidateAssignmentRequest(senior.slotId, nurseId), admin)
            .hardViolations.any { it.code == "CROSS_TEAM_NOT_ALLOWED" })
        staff.assignTeam(wardId, nurseId, team, admin)
        val updated = service.getRoster(draft.rosterId, admin)
        assertTrue(updated.needsRevalidation)
        assertEquals(current.revision + 1, updated.revision)
        current = updated
        val candidate = service.validate(current.rosterId, tag(current), ValidateAssignmentRequest(senior.slotId, nurseId), admin)
        assertFalse(candidate.allowed)
        assertTrue(candidate.hardViolations.any { it.code == "SENIOR_REQUIRED" })
        val denied = assertThrows(BusinessException::class.java) {
            service.assign(current.rosterId, senior.slotId, tag(current), UUID.randomUUID(), AssignNurseRequest(nurseId), admin)
        }
        assertTrue(denied.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "SENIOR_REQUIRED" })
        jdbc.update("UPDATE roster_slots SET nurse_id=?,locked=TRUE WHERE slot_id=?", nurseId, senior.slotId)
        val confirmation = assertThrows(BusinessException::class.java) {
            service.confirm(current.rosterId, tag(current), UUID.randomUUID(), ConfirmRosterRequest(1), admin)
        }
        assertTrue(confirmation.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "SENIOR_REQUIRED" })
        fun generate(): GenerationJob {
            val job = service.startGeneration(current.rosterId, tag(current), UUID.randomUUID(), StartGenerationRequest(true), admin)
            service.markGenerationRunning(job.jobId)
            service.processGeneration(job.jobId)
            return service.getGenerationJob(job.jobId, admin)
        }
        val locked = generate()
        assertEquals("FAILED", locked.status)
        assertTrue(locked.diagnostics.any { it.code == "SENIOR_REQUIRED" })
        jdbc.update("UPDATE roster_slots SET nurse_id=NULL,locked=FALSE WHERE slot_id=?", senior.slotId)
        val generated = generate()
        assertEquals("PARTIAL", generated.status)
        assertTrue(generated.diagnostics.any { it.code == "SENIOR_UNFILLED" })
        current = service.getRoster(draft.rosterId, admin)
        assertEquals(1, current.staffing.seniorUnfilled)
        assertEquals(nurseId, current.slots.single { !it.seniorRequired }.assignment?.nurseId)
        service.confirm(current.rosterId, tag(current), UUID.randomUUID(), ConfirmRosterRequest(1), admin)
        val published = service.publishedWard(wardId, "2026-11", admin)
        assertEquals(1, published.staffing.seniorUnfilled)
        assertEquals(team, published.slots.single { it.seniorRequired }.teamId)
    }

    @Test
    fun `work pattern is enforced for candidate manual locked generation automatic and confirmation`() {
        jdbc.update("UPDATE nurses SET work_pattern='DAY_EVENING_ONLY' WHERE nurse_id=?", nurseId)
        val draft = newRoster("2026-11", LocalDate.parse("2026-11-30"), nightTypeId)
        val slot = draft.slots.single().slotId
        val validated = service.validate(draft.rosterId, tag(draft), ValidateAssignmentRequest(slot, nurseId), admin)
        assertFalse(validated.allowed)
        assertTrue(validated.hardViolations.any { it.code == "SHIFT_KIND_NOT_ALLOWED" })
        val denied = assertThrows(BusinessException::class.java) { assign(draft, nurseId) }
        assertEquals("RULE_VIOLATION", denied.errorCode.name)
        jdbc.update("UPDATE roster_slots SET nurse_id=?,locked=TRUE WHERE slot_id=?", nurseId, slot)
        val confirmed = assertThrows(BusinessException::class.java) {
            service.confirm(draft.rosterId, tag(draft), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        }
        assertTrue(confirmed.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "SHIFT_KIND_NOT_ALLOWED" })
        val locked = service.startGeneration(draft.rosterId, tag(draft), UUID.randomUUID(), StartGenerationRequest(true), admin)
        service.markGenerationRunning(locked.jobId)
        service.processGeneration(locked.jobId)
        assertEquals("FAILED", service.getGenerationJob(locked.jobId, admin).status)
        jdbc.update("UPDATE roster_slots SET nurse_id=NULL,locked=FALSE WHERE slot_id=?", slot)
        val automatic = service.startGeneration(draft.rosterId, tag(draft), UUID.randomUUID(), StartGenerationRequest(true), admin)
        service.markGenerationRunning(automatic.jobId)
        service.processGeneration(automatic.jobId)
        assertEquals("PARTIAL", service.getGenerationJob(automatic.jobId, admin).status)
        assertEquals(null, service.getRoster(draft.rosterId, admin).slots.single().assignment)
    }

    @BeforeEach
    fun setup() {
        listOf("mutation_replays", "generation_jobs", "approved_absences", "roster_slots", "rosters",
            "shift_types", "staff_adjustments", "nurses", "ward_memberships", "audit_events", "ward_policy_revisions", "ward_teams", "wards")
            .forEach { jdbc.update("DELETE FROM $it") }
        wardId = UUID.randomUUID()
        nurseId = UUID.randomUUID()
        dayTypeId = UUID.randomUUID()
        nightTypeId = UUID.randomUUID()
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", wardId, "테스트 병동", "Asia/Seoul")
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,'WARD_ADMIN',TRUE)",
            wardId, admin)
        // The nurse record is linked to an account, so that account must be an active member to be rostered.
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,'test-nurse','WARD_MEMBER',TRUE)", wardId)
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
        val conflict = assertThrows(BusinessException::class.java) {
            service.confirm(november.rosterId, tag(novemberAssigned), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        }
        assertEquals("RULE_VIOLATION", conflict.errorCode.name)
        assertTrue(conflict.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "REST_AFTER_NIGHT" })
        assertEquals("DRAFT", service.getRoster(november.rosterId, admin).status)
        assertThrows(BusinessException::class.java) { service.publishedWard(wardId, "2026-11", admin) }
    }

    @Test
    fun `candidate manual automatic and confirm apply a common work limit across published months`() {
        val october = newRoster("2026-10", (27..31).map { LocalDate.of(2026, 10, it) }, nightTypeId)
        val job = generation.start(october.rosterId, tag(october), UUID.randomUUID(), StartGenerationRequest(false), admin)
        val generated = waitForGeneration(job)
        assertEquals("COMPLETED", generated.status)
        val assigned = service.getRoster(october.rosterId, admin)
        assertEquals(5, assigned.staffing.assigned)
        service.confirm(october.rosterId, tag(assigned), UUID.randomUUID(), ConfirmRosterRequest(), admin)

        val draft = newRoster("2026-11", LocalDate.parse("2026-11-03"), nightTypeId)
        val slot = draft.slots.single().slotId
        // Blank days and approved annual leave both preserve the preceding work count.
        assertTrue(service.validate(draft.rosterId, tag(draft), ValidateAssignmentRequest(slot, nurseId), admin)
            .hardViolations.any { it.code == "CONSECUTIVE_WORK_LIMIT" })
        service.addAbsence(
            draft.rosterId, tag(draft), UUID.randomUUID(), ApprovedAbsenceInput(
                nurseId, "APPROVED_LEAVE", Instant.parse("2026-11-01T15:00:00Z"), Instant.parse("2026-11-02T15:00:00Z"),
            ), admin,
        )
        val november = service.getRoster(draft.rosterId, admin)
        assertTrue(service.validate(november.rosterId, tag(november), ValidateAssignmentRequest(slot, nurseId), admin)
            .hardViolations.any { it.code == "CONSECUTIVE_WORK_LIMIT" })
        val manual = assertThrows(BusinessException::class.java) { assign(november, nurseId) }
        assertTrue(manual.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "CONSECUTIVE_WORK_LIMIT" })
        val automatic = waitForGeneration(generation.start(
            november.rosterId, tag(november), UUID.randomUUID(), StartGenerationRequest(false), admin,
        ))
        assertEquals("PARTIAL", automatic.status)
        assertEquals(0, automatic.staffing!!.assigned)

        val afterGeneration = service.getRoster(november.rosterId, admin)
        jdbc.update("UPDATE roster_slots SET nurse_id=?,locked=TRUE WHERE slot_id=?", nurseId, slot)
        val invalid = assertThrows(BusinessException::class.java) {
            service.confirm(november.rosterId, tag(afterGeneration), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        }
        assertTrue(invalid.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "CONSECUTIVE_WORK_LIMIT" })
        val locked = waitForGeneration(generation.start(
            november.rosterId, tag(afterGeneration), UUID.randomUUID(), StartGenerationRequest(true), admin,
        ))
        assertEquals("FAILED", locked.status)
        assertTrue(locked.diagnostics.any { it.code == "CONSECUTIVE_WORK_LIMIT" })

        // An OF on Nov 1 resets the run even though the Oct 31 night ends on its morning.
        val rest = RosterRestDay(nurseId, LocalDate.parse("2026-11-01"), RestDayKind.OF)
        service.replaceRestDays(november.rosterId, tag(afterGeneration), listOf(rest), admin)
        val withRest = service.getRoster(november.rosterId, admin)
        service.confirm(november.rosterId, tag(withRest), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        val replacement = service.createRoster(wardId, CreateRosterRequest("2026-11"), admin, UUID.randomUUID())
        assertEquals(listOf(rest), service.listRestDays(replacement.rosterId, admin).items)
    }

    @Test
    fun `publishing a month without assignments for a nurse cannot join disjoint unsafe runs`() {
        val oct = newRoster("2026-10", (28..31).map { LocalDate.of(2026, 10, it) }, nightTypeId)
        val dec = newRoster("2026-12", (1..2).map { LocalDate.of(2026, 12, it) }, nightTypeId)
        for (roster in listOf(oct, dec)) {
            val filled = roster.slots.fold(roster) { current, slot ->
                service.assign(current.rosterId, slot.slotId, tag(current), UUID.randomUUID(), AssignNurseRequest(nurseId), admin)
            }
            service.confirm(filled.rosterId, tag(filled), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        }
        val gap = newRoster("2026-11", LocalDate.parse("2026-11-15"), nightTypeId)
        val rejected = assertThrows(BusinessException::class.java) {
            service.confirm(gap.rosterId, tag(gap), UUID.randomUUID(), ConfirmRosterRequest(1), admin)
        }
        assertTrue(rejected.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "CONSECUTIVE_WORK_LIMIT" })
    }

    @Test
    fun `editing a shift type never rewrites a published roster or the rest rules checked against it`() {
        val october = assign(newRoster("2026-10", LocalDate.parse("2026-10-31"), nightTypeId), nurseId)
        val november = assign(newRoster("2026-11", LocalDate.parse("2026-11-01"), dayTypeId), nurseId)
        service.confirm(october.rosterId, tag(october), UUID.randomUUID(), ConfirmRosterRequest(), admin)

        // The published night type is turned into a day type with another code.
        shiftTypes.update(wardId, nightTypeId, ShiftTypeCommand("N2", "데이로 바뀐 유형", ShiftKind.DAY,
            java.time.LocalTime.of(7, 0), java.time.LocalTime.of(15, 0), false, null, null, null, null),
            ShiftTypeService.policyEtag(0), admin)

        val published = service.publishedMine(wardId, "2026-10", "test-nurse").assignments.single()
        assertEquals("NIGHT", published.shiftType)
        assertEquals(Instant.parse("2026-10-31T10:00:00Z"), published.startsAt)
        val conflict = assertThrows(BusinessException::class.java) {
            service.confirm(november.rosterId, tag(november), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        }
        assertTrue(conflict.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "REST_AFTER_NIGHT" })
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
                    } catch (ex: BusinessException) {
                        assertEquals("RULE_VIOLATION", ex.errorCode.name)
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

        val conflict = assertThrows(BusinessException::class.java) {
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
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,'test-nurse-2','WARD_MEMBER',TRUE)", wardId)
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
        assertThrows(BusinessException::class.java) { service.publishedWard(wardId, "2026-11", admin) }

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
        val emptyConfirmation = assertThrows(BusinessException::class.java) {
            service.confirm(replacement.rosterId, tag(replacement), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        }
        assertEquals("ROSTER_REQUIREMENTS_REQUIRED", emptyConfirmation.errorCode.name)
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
        val conflict = assertThrows(BusinessException::class.java) {
            service.assign(replacement.rosterId, slot.slotId,
                "\"${service.etag(replacement.rosterId, requirements.revision)}\"",
                UUID.randomUUID(), AssignNurseRequest(nurseId), admin)
        }
        assertEquals("RULE_VIOLATION", conflict.errorCode.name)
        assertTrue(conflict.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "APPROVED_LEAVE_OVERLAP" })
        assertEquals("PUBLISHED", service.getRoster(original.rosterId, admin).status)
        assertEquals(1, service.listAbsences(original.rosterId, admin).size)
    }

    @Test
    fun `assign replays the same idempotency key without a second mutation`() {
        val secondNurse = UUID.randomUUID()
        jdbc.update("INSERT INTO nurses(nurse_id,ward_id,subject,display_name,qualifications,active) VALUES(?,?,?,?,?,TRUE)",
            secondNurse, wardId, "test-nurse-2", "간호사 2", "[]")
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,'test-nurse-2','WARD_MEMBER',TRUE)", wardId)
        val draft = newRoster("2026-11", LocalDate.parse("2026-11-02"), dayTypeId)
        val key = UUID.randomUUID()
        val slotId = draft.slots.single().slotId

        val first = service.assign(draft.rosterId, slotId, tag(draft), key, AssignNurseRequest(nurseId), admin)
        val replayed = service.assign(draft.rosterId, slotId, tag(draft), key, AssignNurseRequest(nurseId), admin)
        assertEquals(first, replayed)
        assertEquals(draft.revision + 1, service.getRoster(draft.rosterId, admin).revision)

        val reused = assertThrows(BusinessException::class.java) {
            service.assign(draft.rosterId, slotId, tag(draft), key, AssignNurseRequest(secondNurse), admin)
        }
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.errorCode.name)
        val current = service.getRoster(draft.rosterId, admin)
        assertEquals(draft.revision + 1, current.revision)
        assertEquals(nurseId, current.slots.single().assignment?.nurseId)
    }

    @Test
    fun `assign retry replays a response saved before slots carried their code and kind`() {
        val draft = newRoster("2026-11", LocalDate.parse("2026-11-02"), dayTypeId)
        val key = UUID.randomUUID()
        val slotId = draft.slots.single().slotId
        val first = service.assign(draft.rosterId, slotId, tag(draft), key, AssignNurseRequest(nurseId), admin)
        // The response as an earlier server stored it, and a later type edit the slot's own values do not follow.
        val saved = jdbc.queryForObject(
            "SELECT response_json FROM mutation_replays WHERE idempotency_key=?", String::class.java, key)!!
        val legacy = saved.replace(""""shiftCode":"DAY",""", "").replace(""""shiftKind":"DAY",""", "")
        assertFalse(legacy.contains("shiftCode") || legacy.contains("shiftKind"), legacy)
        jdbc.update("UPDATE mutation_replays SET response_json=? WHERE idempotency_key=?", legacy, key)
        jdbc.update("UPDATE shift_types SET code='D2',kind='EVENING' WHERE shift_type_id=?", dayTypeId)

        val replayed = service.assign(draft.rosterId, slotId, tag(draft), key, AssignNurseRequest(nurseId), admin)
        assertEquals(first, replayed)
        assertEquals("DAY" to "DAY", replayed.slots.single().let { it.shiftCode to it.shiftKind })
        assertEquals(draft.revision + 1, service.getRoster(draft.rosterId, admin).revision)
    }

    private fun newRoster(month: String, date: LocalDate, shiftTypeId: UUID): RosterView =
        newRoster(month, listOf(date), shiftTypeId)

    private fun newRoster(month: String, dates: List<LocalDate>, shiftTypeId: UUID): RosterView {
        val draft = service.createRoster(wardId, CreateRosterRequest(month), admin, UUID.randomUUID())
        service.replaceRequirements(draft.rosterId, tag(draft), ReplaceRequirementsRequest(dates.map {
            StaffingRequirement(it, shiftTypeId, 1, emptyList())
        }), admin)
        return service.getRoster(draft.rosterId, admin)
    }

    private fun waitForGeneration(job: GenerationJob): GenerationJob {
        repeat(100) {
            val current = service.getGenerationJob(job.jobId, admin)
            if (current.status !in setOf("QUEUED", "RUNNING")) return current
            Thread.sleep(50)
        }
        throw AssertionError("Generation job did not finish")
    }

    private fun assign(roster: RosterView, nurse: UUID): RosterView = service.assign(
        roster.rosterId, roster.slots.single().slotId, tag(roster), UUID.randomUUID(), AssignNurseRequest(nurse), admin,
    )

    private fun tag(roster: RosterView): String = "\"${service.etag(roster.rosterId, roster.revision)}\""
}
