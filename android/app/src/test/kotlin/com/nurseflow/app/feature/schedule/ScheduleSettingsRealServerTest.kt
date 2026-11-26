package com.nurseflow.app.feature.schedule

import com.nurseflow.app.core.network.*
import com.nurseflow.app.core.session.MemoryTokenStore
import com.nurseflow.app.core.session.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import retrofit2.Response
import java.util.UUID

/** Real API/DB/auth; fictional OIDC identities. Run only against the isolated loopback fixture. */
class ScheduleSettingsRealServerTest {
    private fun <T> body(r: Response<T>): T { assertTrue("HTTP ${r.code()}", r.isSuccessful); return requireNotNull(r.body()) }
    private fun tag(r: Response<*>) = requireNotNull(r.headers()["ETag"])
    @Test fun settingsRoundTripRevisionConflictAndMemberPermissions() = runBlocking {
        assumeTrue("Start B5a loopback fixture and opt in", System.getenv("NURSEFLOW_B7A_E2E") == "1")
        val base = "http://127.0.0.1:18091/"
        val suffix = UUID.randomUUID().toString()
        suspend fun login(role: String): SessionManager {
            val raw = withContext(Dispatchers.IO) { OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:18090/id-token?account=$role-$suffix").build()).execute().use { it.body!!.string() } }
            val token = Json.parseToJsonElement(raw).jsonObject.getValue("idToken").jsonPrimitive.content
            return SessionManager(ApiFactory.authApi(base), MemoryTokenStore()).also { it.loginWithKakao(token, "fictional-nonce") }
        }
        val admin = login("admin"); val member = login("member")
        val creation = ApiFactory.wardCreationApi(base, admin)
        val hospital = creation.searchHospitals("365").items.first()
        val ward = creation.createWard(UUID.randomUUID().toString(), CreateWardBody(HospitalRefBody(hospital.source, hospital.externalId), "B7a 가상 병동 $suffix"))
        val w = ward.wardId
        val staff = ApiFactory.staffAdminApi(base, admin)
        val team = staff.createTeam(w, AdminTeamInput("가상 A팀", 1))
        val nurse = staff.createStaff(w, AdminStaffInput("가상 간호사"))
        staff.assignTeam(w, nurse.nurseId, AdminTeamAssignment(team.teamId))
        staff.settings(w, nurse.nurseId, AdminRosterSettings(true, 4))
        val api = ApiFactory.scheduleSettingsApi(base, admin)
        val originalPolicy = api.shifts(w)
        val input = ScheduleShiftInput("D", "가상 Day", "DAY", "07:00", "15:00", false, 30, 450, 420)
        val created = api.createShift(w, tag(originalPolicy), UUID.randomUUID().toString(), input)
        val shift = body(created)
        assertEquals(420, shift.targetCreditMinutes)
        assertEquals(412, api.updateShift(w, shift.shiftTypeId, tag(originalPolicy), input).code())
        val draftResponse = api.createRoster(w, UUID.randomUUID().toString(), ScheduleMonth("2026-11"))
        val roster = body(draftResponse)
        val requirements = api.requirements(roster.rosterId, tag(draftResponse), ScheduleRequirements(listOf(ScheduleRequirement("2026-11-05", shift.shiftTypeId, 2, emptyList(), team.teamId))))
        assertEquals(2, body(requirements).staffing.required)
        val full = api.roster(roster.rosterId)
        assertEquals(1, body(full).slots!!.count { it.seniorRequired })
        val preview = body(api.preview(w, shift.shiftTypeId, input.copy(targetCreditMinutes = 400)))
        assertTrue(preview.affectedDrafts.any { it.month == "2026-11" })
        val policy = api.shifts(w)
        body(api.updateShift(w, shift.shiftTypeId, tag(policy), input.copy(targetCreditMinutes = 400)))
        val current = api.roster(roster.rosterId)
        assertTrue(body(current).revision > roster.revision)
        assertTrue(body(current).needsRevalidation)
        assertEquals(412, api.requirements(roster.rosterId, tag(full), ScheduleRequirements(emptyList())).code())
        val absenceInput = ScheduleAbsenceInput(nurse.nurseId, "APPROVED_HALF_DAY", "2026-11-06T00:00:30.123456789Z", "2026-11-06T04:00:30.987654321Z")
        val absence = body(api.createAbsence(roster.rosterId, tag(current), UUID.randomUUID().toString(), absenceInput))
        assertEquals(absenceInput.startsAt, absence.startsAt)
        val absenceDraft = api.roster(roster.rosterId)
        body(api.updateAbsence(roster.rosterId, absence.absenceId, tag(absenceDraft), absenceInput.copy(kind = "APPROVED_LEAVE")))
        assertEquals(absenceInput.startsAt, body(api.absences(roster.rosterId)).items.single().startsAt)
        assertEquals("NIGHT_ONLY", body(api.savePattern(w, nurse.nurseId, SchedulePattern("NIGHT_ONLY"))).workPattern)
        assertEquals("NIGHT_ONLY", body(api.pattern(w, nurse.nurseId)).workPattern)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val model = ScheduleSettingsViewModel(api, w, { ApiFactory.nurseFlowApi(base, admin).wards().items }, scope = scope)
            model.setMonth("2026-11"); model.activate()
            val loaded = withTimeout(20_000) { model.state.first { it.editable || (it.notice != null && !it.loading) } }
            assertTrue(loaded.notice, loaded.editable)
            assertEquals(400, loaded.data!!.shifts.value.items.single().targetCreditMinutes)
            assertEquals(absenceInput.startsAt, loaded.data.absences.single().startsAt)
            model.selectNurse(nurse.nurseId)
            assertEquals("NIGHT_ONLY", withTimeout(10_000) { model.state.first { !it.busy && it.nurseId != null } }.pattern)
            model.savePattern("DAY_EVENING_ONLY")
            val saved = withTimeout(10_000) { model.state.first { !it.busy && it.pattern == "DAY_EVENING_ONLY" } }
            assertTrue(saved.editable)
            model.deactivate(); model.activate()
            assertTrue(withTimeout(10_000) { model.state.first { it.editable } }.data!!.absences.isNotEmpty())
            model.deactivate()
        } finally { scope.cancel() }
        val deleteTag = tag(api.roster(roster.rosterId))
        assertEquals(204, api.deleteAbsence(roster.rosterId, absence.absenceId, deleteTag).code())
        assertTrue(body(api.absences(roster.rosterId)).items.isEmpty())
        val latestPolicy = api.shifts(w)
        assertFalse(body(api.deactivateShift(w, shift.shiftTypeId, tag(latestPolicy))).active)
        val inactivePolicy = api.shifts(w)
        assertTrue(body(api.updateShift(w, shift.shiftTypeId, tag(inactivePolicy), input)).active)
        val nightInput = ScheduleShiftInput("N", "가상 Night", "NIGHT", "22:00", "07:00", true, 0, 540, 540)
        assertEquals(540, body(api.createShift(w, tag(api.shifts(w)), UUID.randomUUID().toString(), nightInput)).durationMinutes)
        val invitation = ApiFactory.invitationApi(base, admin).create(w, CreateInvitationBody("WARD_MEMBER", nurse.nurseId))
        ApiFactory.invitationApi(base, member).redeem(RedeemInvitationBody(invitation.token, "2024-01"))
        val memberApi = ApiFactory.scheduleSettingsApi(base, member)
        // Shift type listing is a ward-member read API; mutations remain admin-only.
        assertEquals(200, memberApi.shifts(w).code())
        assertEquals(403, memberApi.createShift(w, tag(api.shifts(w)), UUID.randomUUID().toString(), input.copy(code = "X")).code())
        assertEquals(403, memberApi.savePattern(w, nurse.nurseId, SchedulePattern("GENERAL")).code())
    }
}
