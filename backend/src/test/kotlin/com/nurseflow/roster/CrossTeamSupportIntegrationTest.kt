package com.nurseflow.roster

import com.nurseflow.common.error.BusinessException
import com.nurseflow.roster.application.model.*
import com.nurseflow.roster.application.service.RosterService
import com.nurseflow.roster.application.service.SupportAllowanceInput
import com.nurseflow.roster.application.service.SupportAllowanceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.LocalDate
import java.util.UUID

/** A14: other-team slots only within an admin allowance, on all four paths (candidate, manual, automatic, confirm). */
@SpringBootTest(properties = ["spring.datasource.url=jdbc:h2:mem:cross_team_support;DB_CLOSE_DELAY=-1"])
class CrossTeamSupportIntegrationTest @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val service: RosterService,
    private val allowances: SupportAllowanceService,
) {
    private val admin = "test-admin"
    private lateinit var wardId: UUID
    private lateinit var teamA: UUID
    private lateinit var teamB: UUID
    private lateinit var nurseA: UUID
    private lateinit var nurseB: UUID
    private lateinit var dayTypeId: UUID
    private lateinit var nightTypeId: UUID

    @BeforeEach
    fun setup() {
        listOf("cross_team_support_allowances", "mutation_replays", "generation_jobs", "approved_absences", "roster_rest_days",
            "roster_slots", "rosters", "shift_types", "nurses", "ward_memberships", "audit_events", "ward_teams", "wards")
            .forEach { jdbc.update("DELETE FROM $it") }
        wardId = UUID.randomUUID()
        teamA = UUID.randomUUID()
        teamB = UUID.randomUUID()
        dayTypeId = UUID.randomUUID()
        nightTypeId = UUID.randomUUID()
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", wardId, "가상 병동", "Asia/Seoul")
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,'WARD_ADMIN',TRUE)", wardId, admin)
        listOf(teamA to "가상 A팀", teamB to "가상 B팀").forEachIndexed { i, (id, name) ->
            jdbc.update("INSERT INTO ward_teams(team_id,ward_id,name,sort_order,active) VALUES(?,?,?,?,TRUE)", id, wardId, name, i + 1)
        }
        fun nurse(subject: String, team: UUID): UUID = UUID.randomUUID().also {
            jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,'WARD_MEMBER',TRUE)", wardId, subject)
            jdbc.update("""INSERT INTO nurses(nurse_id,ward_id,subject,display_name,qualifications,active,team_id,senior_eligible)
                VALUES(?,?,?,?,'[]',TRUE,?,TRUE)""", it, wardId, subject, "가상 $subject", team)
        }
        nurseA = nurse("nurse-a", teamA)
        nurseB = nurse("nurse-b", teamB)
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'DAY','데이','DAY','07:00:00','15:00:00',FALSE)""", dayTypeId, wardId)
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'NIGHT','나이트','NIGHT','19:00:00','07:00:00',TRUE)""", nightTypeId, wardId)
    }

    @Test
    fun `support needs an allowance on candidate manual locked generation and confirm and revocation flags the draft`() {
        val d10 = LocalDate.parse("2026-12-10")
        val d11 = LocalDate.parse("2026-12-11")
        var roster = draft("2026-12",
            StaffingRequirement(d10, dayTypeId, 1, emptyList(), teamA),
            StaffingRequirement(d11, dayTypeId, 1, emptyList(), teamA),
            StaffingRequirement(d10, dayTypeId, 1, emptyList(), teamB),
            StaffingRequirement(d10, nightTypeId, 1, emptyList(), teamA),
        )
        val a10 = roster.slot(teamA, d10, dayTypeId)
        val a11 = roster.slot(teamA, d11, dayTypeId)
        val b10 = roster.slot(teamB, d10, dayTypeId)
        val a10Night = roster.slot(teamA, d10, nightTypeId)

        // Before any allowance: candidate and manual save refuse.
        assertEquals(listOf("CROSS_TEAM_NOT_ALLOWED"), codes(roster, a10, nurseB))
        val manual = assertThrows(BusinessException::class.java) { assign(roster, a10, nurseB) }
        assertEquals("RULE_VIOLATION", manual.errorCode.name)
        assertTrue(manual.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "CROSS_TEAM_NOT_ALLOWED" })

        val granted = allowances.create(wardId, SupportAllowanceInput(nurseB, teamA, d10, d10, listOf(dayTypeId)), admin).allowance
        roster = service.getRoster(roster.rosterId, admin)
        assertFalse(roster.needsRevalidation, "a grant only widens what is allowed")
        assertTrue(codes(roster, a10, nurseB).isEmpty())
        assertEquals(listOf("CROSS_TEAM_NOT_ALLOWED"), codes(roster, a11, nurseB), "date after the range")
        assertEquals(listOf("CROSS_TEAM_NOT_ALLOWED"), codes(roster, a10Night, nurseB), "shift type outside the range")

        roster = assign(roster, a10, nurseB, locked = true)
        roster = assign(roster, a11, nurseA)
        // The allowance relaxes only the team: the same nurse cannot also take an overlapping own-team shift.
        assertEquals(listOf("SHIFT_TIME_OVERLAP"), codes(roster, b10, nurseB))
        // Support counts toward the placement team's staffing, not the home team's.
        val placed = roster.slots.single { it.slotId == a10 }
        assertEquals(teamA, placed.teamId)
        assertEquals(nurseB, placed.assignment?.nurseId)
        assertEquals(2, roster.staffing.assigned)

        // Same scope again is the existing record, not a twin that would outlive its revocation.
        val again = allowances.create(wardId, SupportAllowanceInput(nurseB, teamA, d10, d10, listOf(dayTypeId)), admin)
        assertFalse(again.created)
        assertEquals(granted.id, again.allowance.id)
        // An allowance for a shift type the draft does not use for this nurse flags nothing.
        val nightOnly = allowances.create(wardId, SupportAllowanceInput(nurseB, teamA, d10, d10, listOf(nightTypeId)), admin)
        assertEquals(0, allowances.revoke(wardId, nightOnly.allowance.id, admin).flaggedDrafts)
        assertEquals(roster.revision, service.getRoster(roster.rosterId, admin).revision)

        val revoked = allowances.revoke(wardId, granted.id, admin)
        assertTrue(revoked.allowance.revoked)
        assertEquals(1, revoked.flaggedDrafts)
        assertEquals(0, allowances.revoke(wardId, granted.id, admin).flaggedDrafts, "revoking again changes nothing")
        val flagged = service.getRoster(roster.rosterId, admin)
        assertTrue(flagged.needsRevalidation)
        assertEquals(roster.revision + 1, flagged.revision)
        roster = flagged

        val locked = generate(roster, preserveLocked = true)
        assertEquals("FAILED", locked.status)
        assertTrue(locked.diagnostics.any { it.code == "CROSS_TEAM_NOT_ALLOWED" })
        val confirm = assertThrows(BusinessException::class.java) {
            service.confirm(roster.rosterId, tag(roster), UUID.randomUUID(), ConfirmRosterRequest(2), admin)
        }
        assertTrue(confirm.ruleViolations.filterIsInstance<RuleDiagnostic>().any { it.code == "CROSS_TEAM_NOT_ALLOWED" })

        // A new grant makes the stored assignment valid again; publishing keeps it even after a later revocation.
        val regranted = allowances.create(wardId, SupportAllowanceInput(nurseB, teamA, d10, d10, emptyList()), admin).allowance
        roster = service.getRoster(roster.rosterId, admin)
        service.confirm(roster.rosterId, tag(roster), UUID.randomUUID(), ConfirmRosterRequest(2), admin)
        assertEquals(0, allowances.revoke(wardId, regranted.id, admin).flaggedDrafts)
        val published = service.publishedWard(wardId, "2026-12", admin)
        assertEquals(nurseB, published.slots.single { it.slotId == a10 }.assignment?.nurseId)
        assertEquals(3, allowances.list(wardId, includeRevoked = true, admin).size)

        // A nurse who has since joined the team holds own-team slots: revoking an old allowance flags nothing.
        val next = draft("2027-02", StaffingRequirement(LocalDate.parse("2027-02-03"), dayTypeId, 1, emptyList(), teamA))
        val old = allowances.create(wardId, SupportAllowanceInput(nurseB, teamA, LocalDate.parse("2027-02-01"),
            LocalDate.parse("2027-02-28"), emptyList()), admin).allowance
        jdbc.update("UPDATE nurses SET team_id=? WHERE nurse_id=?", teamA, nurseB)
        val joined = assign(service.getRoster(next.rosterId, admin), next.slots.single().slotId, nurseB)
        assertEquals(0, allowances.revoke(wardId, old.id, admin).flaggedDrafts)
        assertEquals(joined.revision, service.getRoster(next.rosterId, admin).revision)
        assertTrue(allowances.list(wardId, includeRevoked = false, admin).isEmpty())
    }

    @Test
    fun `automatic generation prefers own team and uses support only inside the allowance`() {
        val d10 = LocalDate.parse("2027-01-10")
        val d11 = LocalDate.parse("2027-01-11")
        allowances.create(wardId, SupportAllowanceInput(nurseB, teamA, d10, d10, emptyList()), admin)
        var roster = draft("2027-01",
            StaffingRequirement(d10, dayTypeId, 1, emptyList(), teamA),
            StaffingRequirement(d11, dayTypeId, 1, emptyList(), teamA),
        )
        // Own-team nurse available: support is not used even though it is allowed.
        assertEquals("COMPLETED", generate(roster, preserveLocked = false).status)
        roster = service.getRoster(roster.rosterId, admin)
        assertTrue(roster.slots.all { it.assignment?.nurseId == nurseA })

        jdbc.update("UPDATE nurses SET active=FALSE WHERE nurse_id=?", nurseA)
        val partial = generate(roster, preserveLocked = false)
        assertEquals("PARTIAL", partial.status)
        roster = service.getRoster(roster.rosterId, admin)
        assertEquals(nurseB, roster.slots.single { it.date == d10 }.assignment?.nurseId)
        assertEquals(null, roster.slots.single { it.date == d11 }.assignment, "outside the allowance stays unfilled")
    }

    private fun draft(month: String, vararg items: StaffingRequirement): RosterView {
        val created = service.createRoster(wardId, CreateRosterRequest(month), admin, UUID.randomUUID())
        service.replaceRequirements(created.rosterId, tag(created), ReplaceRequirementsRequest(items.toList()), admin)
        return service.getRoster(created.rosterId, admin)
    }

    private fun RosterView.slot(team: UUID, date: LocalDate, type: UUID) =
        slots.single { it.teamId == team && it.date == date && it.shiftTypeId == type }.slotId

    private fun codes(roster: RosterView, slot: UUID, nurse: UUID) =
        service.validate(roster.rosterId, tag(roster), ValidateAssignmentRequest(slot, nurse), admin).hardViolations.map { it.code }

    private fun assign(roster: RosterView, slot: UUID, nurse: UUID, locked: Boolean = false) =
        service.assign(roster.rosterId, slot, tag(roster), UUID.randomUUID(), AssignNurseRequest(nurse, locked), admin)

    private fun generate(roster: RosterView, preserveLocked: Boolean): GenerationJob {
        val job = service.startGeneration(roster.rosterId, tag(roster), UUID.randomUUID(), StartGenerationRequest(preserveLocked), admin)
        service.markGenerationRunning(job.jobId)
        service.processGeneration(job.jobId)
        return service.getGenerationJob(job.jobId, admin)
    }

    private fun tag(roster: RosterView) = "\"${service.etag(roster.rosterId, roster.revision)}\""
}
