package com.nurseflow.roster

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.roster.application.model.*
import com.nurseflow.roster.application.service.RosterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A16: unfilled slots and target-hour shortfalls are acknowledged one by one, rechecked, and stored with the revision. */
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:confirm_acknowledgements;DB_CLOSE_DELAY=-1",
])
class ConfirmAcknowledgementIntegrationTest @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val service: RosterService,
) {
    private lateinit var wardId: UUID
    private lateinit var nurseA: UUID
    private lateinit var nurseB: UUID
    private lateinit var dayTypeId: UUID
    private val admin = "ack-admin"

    @BeforeEach
    fun setup() {
        listOf("mutation_replays", "generation_jobs", "approved_absences", "roster_slots", "roster_confirmation_items",
            "roster_confirmations", "rosters", "ward_target_hour_policies", "shift_types", "staff_adjustments", "off_requests",
            "nurses", "ward_memberships", "audit_events", "ward_policy_revisions", "ward_teams", "wards")
            .forEach { jdbc.update("DELETE FROM $it") }
        wardId = UUID.randomUUID()
        nurseA = UUID.randomUUID()
        nurseB = UUID.randomUUID()
        dayTypeId = UUID.randomUUID()
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", wardId, "확인 병동", "Asia/Seoul")
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,'WARD_ADMIN',TRUE)", wardId, admin)
        for ((id, subject, name) in listOf(Triple(nurseA, "ack-nurse-a", "가상 간호사 가"), Triple(nurseB, "ack-nurse-b", "가상 간호사 나"))) {
            jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,'WARD_MEMBER',TRUE)", wardId, subject)
            jdbc.update("INSERT INTO nurses(nurse_id,ward_id,subject,display_name,qualifications,active) VALUES(?,?,?,?,'[]',TRUE)",
                id, wardId, subject, name)
        }
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'DAY','데이','DAY','07:00:00','15:00:00',FALSE)""", dayTypeId, wardId)
    }

    @Test
    fun `each category is listed separately and must be acknowledged before publishing`() {
        // Three Day slots: one filled by A, one general and one senior slot left empty.
        var draft = draft(LocalDate.parse("2026-12-01"), LocalDate.parse("2026-12-02"), LocalDate.parse("2026-12-03"))
        val (filled, general, senior) = draft.slots
        jdbc.update("UPDATE roster_slots SET senior_required=TRUE WHERE slot_id=?", senior.slotId)
        draft = service.assign(draft.rosterId, filled.slotId, tag(draft), UUID.randomUUID(), AssignNurseRequest(nurseA), admin)

        val check = service.confirmationCheck(draft.rosterId, admin)
        assertTrue(check.confirmable)
        assertEquals(listOf(general.slotId), check.generalUnfilled.map { it.slotId })
        assertEquals(listOf(senior.slotId), check.seniorUnfilled.map { it.slotId })
        assertEquals(ConfirmationReasons.SENIOR_UNFILLED, check.seniorUnfilled.single().reason)
        // Both nurses are short of the December target; B has no shift at all, so B is short by more and listed first.
        assertEquals(listOf(nurseB, nurseA), check.hourShortfalls.map { it.nurseId })
        val a = check.hourShortfalls.single { it.nurseId == nurseA }
        assertEquals(a.targetMinutes - a.creditMinutes, a.shortfallMinutes)

        fun reject(request: ConfirmRosterRequest) = assertThrows(BusinessException::class.java) {
            service.confirm(draft.rosterId, tag(draft), UUID.randomUUID(), request, admin)
        }.errorCode
        val all = check.acknowledgeAll().acknowledgements!!
        assertEquals(ErrorCode.UNFILLED_ACK_REQUIRED, reject(ConfirmRosterRequest()))
        assertEquals(ErrorCode.UNFILLED_ACK_REQUIRED, reject(ConfirmRosterRequest(all.copy(seniorUnfilledSlotIds = null))))
        assertEquals(ErrorCode.HOUR_SHORTFALL_ACK_REQUIRED, reject(ConfirmRosterRequest(all.copy(hourShortfalls = emptyList()))))
        // A slot acknowledged under the wrong card, a missing nurse, or a different size is a mismatch.
        assertEquals(ErrorCode.CONFIRM_ACK_MISMATCH, reject(ConfirmRosterRequest(all.copy(
            generalUnfilledSlotIds = listOf(senior.slotId), seniorUnfilledSlotIds = listOf(general.slotId)))))
        assertEquals(ErrorCode.CONFIRM_ACK_MISMATCH, reject(ConfirmRosterRequest(all.copy(hourShortfalls = all.hourShortfalls!!.take(1)))))
        assertEquals(ErrorCode.CONFIRM_ACK_MISMATCH, reject(ConfirmRosterRequest(all.copy(
            hourShortfalls = all.hourShortfalls!!.map { it.copy(shortfallMinutes = it.shortfallMinutes - 60) }))))
        assertEquals("DRAFT", service.getRoster(draft.rosterId, admin).status)
        assertThrows(BusinessException::class.java) { service.confirmation(draft.rosterId, admin) }.also {
            assertEquals(ErrorCode.CONFIRMATION_RECORD_NOT_FOUND, it.errorCode)
        }

        val published = service.confirm(draft.rosterId, tag(draft), UUID.randomUUID(), check.acknowledgeAll(), admin)
        assertEquals("PUBLISHED", published.status)

        val record = service.confirmation(draft.rosterId, admin)
        assertEquals(published.revision, record.revision)
        assertEquals("2026-12", record.month)
        assertEquals(check.generalUnfilled, record.generalUnfilled)
        assertEquals(check.seniorUnfilled, record.seniorUnfilled)
        assertEquals(check.hourShortfalls, record.hourShortfalls)
    }

    @Test
    fun `the record is a snapshot that later policy changes do not rewrite`() {
        var draft = draft(LocalDate.parse("2026-12-01"))
        draft = service.assign(draft.rosterId, draft.slots.single().slotId, tag(draft), UUID.randomUUID(), AssignNurseRequest(nurseA), admin)
        val check = service.confirmationCheck(draft.rosterId, admin)
        service.confirm(draft.rosterId, tag(draft), UUID.randomUUID(), check.acknowledgeAll(), admin)

        setDailyStandard(60)
        assertEquals(check.hourShortfalls, service.confirmation(draft.rosterId, admin).hourShortfalls)
        assertTrue(service.confirmationCheck(draft.rosterId, admin).hourShortfalls.map { it.shortfallMinutes } != check.hourShortfalls.map { it.shortfallMinutes })
    }

    @Test
    fun `a diagnosis that changed after the admin looked is rejected even at the same revision`() {
        var draft = draft(LocalDate.parse("2026-12-01"))
        draft = service.assign(draft.rosterId, draft.slots.single().slotId, tag(draft), UUID.randomUUID(), AssignNurseRequest(nurseA), admin)
        val seen = service.confirmationCheck(draft.rosterId, admin).acknowledgeAll()

        // The ward target changes; the roster revision does not.
        setDailyStandard(420)
        val stale = assertThrows(BusinessException::class.java) {
            service.confirm(draft.rosterId, tag(draft), UUID.randomUUID(), seen, admin)
        }
        assertEquals(ErrorCode.CONFIRM_ACK_MISMATCH, stale.errorCode)
        assertEquals("DRAFT", service.getRoster(draft.rosterId, admin).status)

        service.confirm(draft.rosterId, tag(draft), UUID.randomUUID(), service.confirmationCheck(draft.rosterId, admin).acknowledgeAll(), admin)
        assertEquals("PUBLISHED", service.getRoster(draft.rosterId, admin).status)
    }

    @Test
    fun `a roster with nothing to acknowledge confirms with an empty request and rejects stray acknowledgements`() {
        var draft = draft(LocalDate.parse("2026-12-01"))
        draft = service.assign(draft.rosterId, draft.slots.single().slotId, tag(draft), UUID.randomUUID(), AssignNurseRequest(nurseA), admin)
        jdbc.update("UPDATE nurses SET active=FALSE WHERE nurse_id=?", nurseB)
        // One minute a day: A's single Day shift is over the target, so nobody is short.
        setDailyStandard(1)

        val check = service.confirmationCheck(draft.rosterId, admin)
        assertTrue(check.generalUnfilled.isEmpty() && check.seniorUnfilled.isEmpty() && check.hourShortfalls.isEmpty())
        val stray = assertThrows(BusinessException::class.java) {
            service.confirm(draft.rosterId, tag(draft), UUID.randomUUID(),
                ConfirmRosterRequest(ConfirmAcknowledgements(generalUnfilledSlotIds = listOf(UUID.randomUUID()))), admin)
        }
        assertEquals(ErrorCode.CONFIRM_ACK_MISMATCH, stray.errorCode)

        service.confirm(draft.rosterId, tag(draft), UUID.randomUUID(), ConfirmRosterRequest(), admin)
        val record = service.confirmation(draft.rosterId, admin)
        assertTrue(record.generalUnfilled.isEmpty() && record.seniorUnfilled.isEmpty() && record.hourShortfalls.isEmpty())
    }

    @Test
    fun `hard rule violations block confirmation however much is acknowledged`() {
        var draft = draft(LocalDate.parse("2026-12-01"))
        draft = service.assign(draft.rosterId, draft.slots.single().slotId, tag(draft), UUID.randomUUID(), AssignNurseRequest(nurseA), admin)
        jdbc.update("UPDATE nurses SET active=FALSE WHERE nurse_id=?", nurseA)

        val check = service.confirmationCheck(draft.rosterId, admin)
        assertFalse(check.confirmable)
        assertTrue(check.blockingViolations.any { it.code == "NURSE_INACTIVE" })
        val rejected = assertThrows(BusinessException::class.java) {
            service.confirm(draft.rosterId, tag(draft), UUID.randomUUID(), check.acknowledgeAll(), admin)
        }
        assertEquals(ErrorCode.RULE_VIOLATION, rejected.errorCode)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM roster_confirmations", Int::class.java))
    }

    @Test
    fun `a retried confirm replays the first result and stores one record`() {
        val draft = draft(LocalDate.parse("2026-12-01"))
        val request = service.confirmationCheck(draft.rosterId, admin).acknowledgeAll()
        val key = UUID.randomUUID()

        val first = service.confirm(draft.rosterId, tag(draft), key, request, admin)
        val retry = service.confirm(draft.rosterId, tag(draft), key, request, admin)

        assertEquals(first, retry)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM roster_confirmations", Int::class.java))
        assertEquals(first.revision, service.confirmation(draft.rosterId, admin).revision)
        // A fresh key after publication is a new command on a roster that is no longer a draft.
        val again = assertThrows(BusinessException::class.java) {
            service.confirm(draft.rosterId, "\"${service.etag(first.rosterId, first.revision)}\"", UUID.randomUUID(), request, admin)
        }
        assertEquals(ErrorCode.NOT_DRAFT, again.errorCode)
    }

    @Test
    fun `only ward admins see the check and the record`() {
        val draft = draft(LocalDate.parse("2026-12-01"))
        service.confirm(draft.rosterId, tag(draft), UUID.randomUUID(), service.confirmationCheck(draft.rosterId, admin).acknowledgeAll(), admin)

        assertEquals(ErrorCode.WARD_ADMIN_REQUIRED,
            assertThrows(BusinessException::class.java) { service.confirmation(draft.rosterId, "ack-nurse-a") }.errorCode)
        assertEquals(ErrorCode.WARD_ADMIN_REQUIRED,
            assertThrows(BusinessException::class.java) { service.confirmationCheck(draft.rosterId, "ack-nurse-a") }.errorCode)
        assertEquals(ErrorCode.WARD_NOT_FOUND,
            assertThrows(BusinessException::class.java) { service.confirmation(draft.rosterId, "outsider") }.errorCode)
    }

    private fun draft(vararg dates: LocalDate): RosterView {
        val created = service.createRoster(wardId, CreateRosterRequest("2026-12"), admin, UUID.randomUUID())
        service.replaceRequirements(created.rosterId, tag(created), ReplaceRequirementsRequest(dates.map {
            StaffingRequirement(it, dayTypeId, 1, emptyList())
        }), admin)
        return service.getRoster(created.rosterId, admin)
    }

    private fun setDailyStandard(minutes: Int) {
        jdbc.update("DELETE FROM ward_target_hour_policies WHERE ward_id=?", wardId)
        jdbc.update(
            """INSERT INTO ward_target_hour_policies(ward_id,daily_standard_minutes,leave_credit_minutes,half_day_credit_minutes,
               sof_credit_minutes,updated_by,updated_at) VALUES(?,?,480,240,480,?,?)""",
            wardId, minutes, admin, Instant.now().toString(),
        )
    }

    private fun tag(roster: RosterView): String = "\"${service.etag(roster.rosterId, roster.revision)}\""
}
