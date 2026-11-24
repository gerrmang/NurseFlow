package com.nurseflow.app.feature.staff

import com.nurseflow.app.core.network.*
import com.nurseflow.app.core.session.MemoryTokenStore
import com.nurseflow.app.core.session.SessionManager
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.UUID

/** Opt-in: real Spring backend and isolated memory DB; only the OIDC identities are fictional. */
class StaffAdminRealServerTest {
    @Test fun teamsCareerHistoryMembershipAndPermissionLoss() = runBlocking {
        assumeTrue("Start the isolated B5a fixture and opt in", System.getenv("NURSEFLOW_B5A_E2E") == "1")
        val base = "http://127.0.0.1:18091/"
        val id = UUID.randomUUID().toString()
        suspend fun login(role: String): SessionManager {
            val response = withContext(Dispatchers.IO) { OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:18090/id-token?account=$role-$id").build()).execute().use { it.body!!.string() } }
            val token = Json.parseToJsonElement(response).jsonObject.getValue("idToken").jsonPrimitive.content
            return SessionManager(ApiFactory.authApi(base), MemoryTokenStore()).also { it.loginWithKakao(token, "fictional-nonce") }
        }
        val admin = login("admin"); val member = login("member"); val other = login("other")
        val creation = ApiFactory.wardCreationApi(base, admin)
        val hospital = creation.searchHospitals("365").items.first()
        val ward = creation.createWard(UUID.randomUUID().toString(), CreateWardBody(HospitalRefBody(hospital.source, hospital.externalId), "B5a 가상 검증 $id"))
        val w = ward.wardId
        val api = ApiFactory.staffAdminApi(base, admin)
        val teams = listOf(api.createTeam(w, AdminTeamInput("가상 A팀", 1)), api.createTeam(w, AdminTeamInput("가상 B팀", 2)))
        val a = api.createStaff(w, AdminStaffInput("가상 간호사 A"))
        val b = api.createStaff(w, AdminStaffInput("가상 간호사 B"))
        api.assignTeam(w, a.nurseId, AdminTeamAssignment(teams[0].teamId)); api.assignTeam(w, b.nurseId, AdminTeamAssignment(teams[0].teamId))
        api.order(w, teams[0].teamId, AdminTeamOrder(listOf(b.nurseId, a.nurseId)))
        assertEquals(1, api.staff(w).items.first { it.nurseId == b.nurseId }.teamOrder)
        val rank = api.policy(w).ranks.first()
        api.profile(w, a.nurseId, AdminProfileInput("2020-01", rank)); api.confirmProfile(w, a.nurseId)
        api.settings(w, a.nurseId, AdminRosterSettings(true, 4))
        assertEquals(4, api.staff(w).items.first { it.nurseId == a.nurseId }.rosterStage)
        api.settings(w, a.nurseId, AdminRosterSettings(false, null))
        assertNull(api.staff(w).items.first { it.nurseId == a.nurseId }.stageOverride)
        assertTrue(api.adjustments(w, a.nurseId).items.any { it.kind == "STAGE_OVERRIDE" })
        suspend fun expectCode(code: String, block: suspend () -> Unit) {
            try { apiCall { block() }; fail("Expected $code") } catch (e: ApiException) { assertEquals(code, e.code) }
        }
        expectCode("TEAM_NOT_EMPTY") { api.deleteTeam(w, teams[0].teamId) }
        api.assignTeam(w, b.nurseId, AdminTeamAssignment(teams[1].teamId))
        expectCode("TEAM_ORDER_STALE") { api.order(w, teams[0].teamId, AdminTeamOrder(listOf(a.nurseId, b.nurseId))) }
        api.assignTeam(w, b.nurseId, AdminTeamAssignment(null))
        assertNull(api.staff(w).items.first { it.nurseId == b.nurseId }.team)
        val invites = ApiFactory.invitationApi(base, admin)
        val link = invites.create(w, CreateInvitationBody("WARD_MEMBER", a.nurseId))
        ApiFactory.invitationApi(base, member).redeem(RedeemInvitationBody(link.token, "2024-01"))
        assertEquals("2024-01", api.staff(w).items.first { it.nurseId == a.nurseId }.profile.hospitalJoinedOn)
        val adminId = (admin.state.value as com.nurseflow.app.core.session.SessionState.SignedIn).userId
        val memberId = (member.state.value as com.nurseflow.app.core.session.SessionState.SignedIn).userId
        expectCode("LAST_WARD_ADMIN") { api.membership(w, adminId, AdminMembershipInput(active = false)) }
        expectCode("ADMIN_NURSE_RECORD_CONFLICT") { api.membership(w, memberId, AdminMembershipInput(admin = true)) }
        api.updateStaff(w, a.nurseId, AdminStaffInput(a.displayName, active = false))
        api.membership(w, memberId, AdminMembershipInput(admin = true))
        expectCode("ADMIN_NURSE_RECORD_CONFLICT") { api.updateStaff(w, a.nurseId, AdminStaffInput(a.displayName)) }
        api.membership(w, memberId, AdminMembershipInput(admin = false))
        api.membership(w, memberId, AdminMembershipInput(active = false))
        expectCode("MEMBER_INACTIVE") { api.updateStaff(w, a.nurseId, AdminStaffInput(a.displayName)) }
        api.membership(w, memberId, AdminMembershipInput(active = true))
        assertFalse(api.staff(w).items.first { it.nurseId == a.nurseId }.active)
        val admin2 = invites.create(w, CreateInvitationBody("WARD_ADMIN"))
        ApiFactory.invitationApi(base, other).redeem(RedeemInvitationBody(admin2.token))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val vm = StaffAdminViewModel(api, w, { ApiFactory.nurseFlowApi(base, admin).wards().items }, scope = scope)
        vm.activate()
        withTimeout(10_000) { while (!vm.state.value.editable) delay(20) }
        vm.membership(adminId, AdminMembershipInput(active = false))
        withTimeout(10_000) { while (!vm.state.value.forbidden) delay(20) }
        assertNull(vm.state.value.data); vm.deactivate(); scope.cancel()
    }
}
