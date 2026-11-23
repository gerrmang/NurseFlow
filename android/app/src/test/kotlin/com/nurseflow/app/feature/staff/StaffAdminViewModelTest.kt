package com.nurseflow.app.feature.staff

import com.nurseflow.app.core.network.*
import com.nurseflow.app.core.session.MemoryTokenStore
import com.nurseflow.app.core.session.SessionManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

private val team = AdminTeam("t1", "가상 A팀", 1)
private fun nurse(id: String = "n1") = AdminStaff(id, "가상 간호사 $id", emptyList(), true, false,
    AdminCareerProfile("MISSING"), team, 1, false)

private class FakeStaffApi : StaffAdminApi {
    var nurses = listOf(nurse(), nurse("n2"))
    var teamRows = listOf(team)
    var write: suspend () -> Unit = {}
    var read: suspend () -> Unit = {}
    var historyRead: suspend () -> Unit = {}
    var writes = 0
    var lastOrder: List<String>? = null
    var lastSettings: AdminRosterSettings? = null
    var lastMembership: AdminMembershipInput? = null
    var lastStaff: AdminStaffInput? = null
    override suspend fun teams(ward: String): Items<AdminTeam> { read(); return Items(teamRows) }
    override suspend fun staff(ward: String) = Items(nurses)
    override suspend fun members(ward: String) = Items(listOf(AdminMember("u1", "가상 관리자", listOf("WARD_ADMIN"), true)))
    override suspend fun policy(ward: String) = AdminCareerPolicy(listOf(12, 36, 72, 120), listOf("간호사"), true)
    var demoMode = false
    var eligibilityRead: suspend () -> Unit = {}
    override suspend fun eligibility(ward: String): AdminEligibility { eligibilityRead(); return AdminEligibility(demoMode, 0) }
    private suspend fun mutate() { writes++; write() }
    override suspend fun createTeam(ward: String, input: AdminTeamInput): AdminTeam { mutate(); return team }
    override suspend fun updateTeam(ward: String, id: String, input: AdminTeamInput): AdminTeam { mutate(); return team }
    override suspend fun deleteTeam(ward: String, id: String) { mutate() }
    override suspend fun createStaff(ward: String, input: AdminStaffInput): AdminStaff { lastStaff = input; mutate(); return nurse() }
    override suspend fun updateStaff(ward: String, id: String, input: AdminStaffInput): AdminStaff { lastStaff = input; mutate(); return nurse(id) }
    override suspend fun assignTeam(ward: String, id: String, input: AdminTeamAssignment): AdminStaff { mutate(); return nurse(id) }
    override suspend fun order(ward: String, id: String, input: AdminTeamOrder): Items<AdminStaff> { lastOrder = input.nurseIds; mutate(); return Items(nurses) }
    override suspend fun profile(ward: String, id: String, input: AdminProfileInput): AdminStaff { mutate(); return nurse(id) }
    override suspend fun confirmProfile(ward: String, id: String): AdminStaff { mutate(); return nurse(id) }
    override suspend fun settings(ward: String, id: String, input: AdminRosterSettings): AdminStaff { lastSettings = input; mutate(); return nurse(id) }
    override suspend fun membership(ward: String, id: String, input: AdminMembershipInput): AdminMember { lastMembership = input; mutate(); return members(ward).items.first() }
    override suspend fun adjustments(ward: String, id: String): Items<AdminAdjustment> { historyRead(); return Items(listOf(AdminAdjustment(id, "TEAM_ORDER", "u1", "2026-01-01T00:00:00Z"))) }
}

class StaffAdminViewModelTest {
    private val api = FakeStaffApi()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var admin = true
    private var valid = true
    private var expired = 0
    private val vm = StaffAdminViewModel(api, "w1", { if (admin) listOf(MembershipDto("w1", "가상 병동", listOf("WARD_ADMIN"), true)) else emptyList() }, { valid }, { expired++ }, scope)
    private fun open() { vm.activate(); assertTrue(vm.state.value.editable) }

    private fun withModeApi(test: suspend (StaffAdminViewModel, MockWebServer) -> Unit) = runBlocking {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            val base = "http://127.0.0.1:${server.port}/"
            val real = ApiFactory.staffAdminApi(base, SessionManager(ApiFactory.authApi(base), MemoryTokenStore()))
            val mixed = object : StaffAdminApi by api {
                override suspend fun eligibility(ward: String) = real.eligibility(ward)
            }
            val testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            val model = StaffAdminViewModel(mixed, "w1", {
                listOf(MembershipDto("w1", "가상 병동", listOf("WARD_ADMIN"), true))
            }, scope = testScope)
            try { withTimeout(5_000) { test(model, server) } }
            finally { model.deactivate(); testScope.cancel() }
        }
    }
    private fun modeResponse(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private suspend fun awaitLoad(model: StaffAdminViewModel) {
        model.state.first { !it.loading && !it.busy }
    }

    @Test fun malformedModeJsonOnEntryStillLoadsEditableLists() = withModeApi { model, server ->
        for (body in listOf("{}", "{", """{"demoMode":{},"policyRevision":0}""")) {
            server.enqueue(modeResponse(body))
            model.activate(); awaitLoad(model)
            assertTrue(model.state.value.editable); assertFalse(model.state.value.stale)
            assertNull(model.state.value.data!!.eligibility)
            assertEquals(api.nurses, model.state.value.data!!.staff)
            assertEquals(api.teamRows, model.state.value.data!!.teams)
            assertEquals("/api/v1/wards/w1/roster-eligibility", server.takeRequest().path)
            model.deactivate()
        }
    }

    @Test fun malformedModeRefreshAndPostWriteReloadKeepListsAndRecover() = withModeApi { model, server ->
        server.enqueue(modeResponse("""{"demoMode":true,"policyRevision":0}"""))
        model.activate(); awaitLoad(model)
        assertTrue(model.state.value.data!!.eligibility!!.demoMode)
        server.enqueue(modeResponse("{}")); model.reload(); awaitLoad(model)
        assertTrue(model.state.value.editable); assertNull(model.state.value.data!!.eligibility)
        server.enqueue(modeResponse("{")); model.saveTeam(null, "가상팀", "1"); awaitLoad(model)
        assertEquals(1, api.writes); assertTrue(model.state.value.editable)
        assertNull(model.state.value.data!!.eligibility)
        assertEquals(api.nurses, model.state.value.data!!.staff)
        server.enqueue(modeResponse("""{"demoMode":false,"policyRevision":0}"""))
        model.reload(); awaitLoad(model)
        assertFalse(model.state.value.data!!.eligibility!!.demoMode)
    }

    @Test fun modeCancellationDoesNotBecomeAnEditableUnknownMode() {
        api.eligibilityRead = { throw CancellationException("가상 조회 취소") }
        vm.activate()
        assertNull(vm.state.value.data); assertFalse(vm.state.value.editable)
        api.eligibilityRead = {}; vm.reload()
        assertTrue(vm.state.value.editable)
    }

    @Test fun demoModeComesFromServerAndRefreshRemovesIt() {
        api.demoMode = true; open()
        assertTrue(vm.state.value.data!!.eligibility!!.demoMode)
        api.demoMode = false; vm.reload()
        assertFalse(vm.state.value.data!!.eligibility!!.demoMode)
    }
    @Test fun failedModeRefreshCannotAppearAsFreshNormalMode() {
        api.demoMode = true; open()
        api.eligibilityRead = { throw ApiException(503, "UNAVAILABLE", "고정") }
        vm.reload()
        assertFalse(vm.state.value.stale); assertTrue(vm.state.value.editable)
        assertNull(vm.state.value.data!!.eligibility)
        assertEquals(api.nurses, vm.state.value.data!!.staff)
        vm.saveTeam(null, "가상팀", "1"); assertEquals(1, api.writes)
        assertTrue(vm.state.value.editable)
        api.eligibilityRead = {}; api.demoMode = false; vm.reload()
        assertFalse(vm.state.value.data!!.eligibility!!.demoMode)
    }
    @Test fun modeUnavailableOnEntryStillLoadsEditableLists() {
        api.eligibilityRead = { throw ApiException(503, "UNAVAILABLE", "고정") }
        open()
        assertNull(vm.state.value.data!!.eligibility)
        assertEquals(api.nurses, vm.state.value.data!!.staff)
    }
    @Test fun modeAccessFailureStillClearsManagementData() {
        for ((status, code) in listOf(403 to "WARD_ADMIN_REQUIRED", 404 to "WARD_NOT_FOUND", 401 to "USER_NOT_FOUND")) {
            api.eligibilityRead = {}; vm.activate(); vm.reload()
            api.eligibilityRead = { throw ApiException(status, code, "고정") }; vm.reload()
            assertTrue(vm.state.value.forbidden); assertNull(vm.state.value.data)
        }
        assertEquals(1, expired)
    }

    @Test fun onlyAdminCanReadAndRevokedPermissionClearsData() {
        open(); admin = false; vm.reload()
        assertTrue(vm.state.value.forbidden); assertNull(vm.state.value.data)
    }
    @Test fun membershipIsRecheckedBeforeWrite() {
        open(); admin = false; vm.saveTeam(null, "가상팀", "1")
        assertEquals(0, api.writes); assertNull(vm.state.value.data)
    }
    @Test fun doubleTapCannotSendConcurrentWrites() {
        open(); val wait = CompletableDeferred<Unit>(); api.write = { wait.await() }
        vm.saveTeam(null, "가상팀", "1"); vm.saveTeam(null, "가상팀", "1")
        assertEquals(1, api.writes); assertTrue(vm.state.value.busy)
        wait.complete(Unit); assertTrue(vm.state.value.editable)
    }
    @Test fun lostResponseRequiresFreshListAndExplicitAcknowledgement() {
        open(); api.write = { throw ApiException(0, "NETWORK", "고정") }
        vm.saveTeam(null, "가상팀", "1")
        assertEquals(1, api.writes); assertTrue(vm.state.value.uncertain); assertFalse(vm.state.value.editable)
        vm.saveTeam(null, "가상팀", "1"); assertEquals(1, api.writes)
        vm.acknowledge(); assertTrue(vm.state.value.editable)
    }
    @Test fun successfulWriteWithFailedReloadStaysBlocked() {
        open(); api.write = { api.read = { throw ApiException(0, "NETWORK", "고정") } }
        vm.saveTeam(null, "가상팀", "1"); assertTrue(vm.state.value.stale); assertFalse(vm.state.value.editable)
        vm.acknowledge(); assertFalse(vm.state.value.editable)
        api.read = {}; vm.reload(); assertTrue(vm.state.value.editable)
    }
    @Test fun invalidSessionDoesNotSendOrApplyWrites() {
        open(); valid = false; vm.saveTeam(null, "가상팀", "1"); assertEquals(0, api.writes)
    }
    @Test fun forbiddenWriteDropsAllManagementAndHistoryData() {
        open(); vm.history("n1"); assertFalse(vm.state.value.history.isEmpty())
        api.write = { throw ApiException(403, "WARD_ADMIN_REQUIRED", "고정") }
        vm.saveTeam(null, "가상팀", "1"); assertTrue(vm.state.value.forbidden)
        assertNull(vm.state.value.data); assertTrue(vm.state.value.history.isEmpty())
    }
    @Test fun unknownAccountEndsSession() {
        open(); api.write = { throw ApiException(401, "USER_NOT_FOUND", "고정") }
        vm.saveTeam(null, "가상팀", "1"); assertEquals(1, expired); assertNull(vm.state.value.data)
    }
    @Test fun orderAlwaysContainsEveryActiveMemberOnce() {
        open(); vm.saveOrder("t1", listOf("n1")); assertEquals(0, api.writes)
        vm.saveOrder("t1", listOf("n1", "n1")); assertEquals(0, api.writes)
        vm.saveOrder("t1", listOf("n2", "n1")); assertEquals(listOf("n2", "n1"), api.lastOrder)
    }
    @Test fun teamCompositionConflictReloadsAuthoritativeMembers() {
        open(); api.write = { api.nurses = listOf(nurse()); throw ApiException(409, "TEAM_ORDER_STALE", "고정") }
        vm.saveOrder("t1", listOf("n2", "n1"))
        assertEquals(1, vm.state.value.data!!.staff.size); assertTrue(vm.state.value.notice!!.contains("팀 구성이"))
    }
    @Test fun explicitNullStageAndFalseSeniorArePreservedTogether() {
        open(); vm.saveSettings("n1", false, null)
        assertEquals(AdminRosterSettings(false, null), api.lastSettings)
    }
    @Test fun membershipChangesOnlyTheRequestedAttribute() {
        open(); vm.membership("u1", AdminMembershipInput(active = false))
        assertEquals(AdminMembershipInput(active = false), api.lastMembership)
        assertNull(api.lastMembership!!.admin)
    }
    @Test fun lastAdminAndStaffConflictErrorsAreActionable() {
        open()
        listOf("LAST_WARD_ADMIN", "ADMIN_NURSE_RECORD_CONFLICT", "MEMBER_INACTIVE", "TEAM_NOT_EMPTY").forEach { code ->
            api.write = { throw ApiException(409, code, "고정") }
            vm.saveTeam(null, "가상팀", "1")
            assertEquals(staffError(code) + "", vm.state.value.notice); assertTrue(vm.state.value.editable)
        }
    }
    @Test fun invalidTeamAndStaffInputsNeverReachServer() {
        open(); vm.saveTeam(null, " ", "1"); vm.saveTeam(null, "가상팀", "0"); vm.saveTeam(null, "가상팀", "1001")
        vm.saveStaff(null, " ", "", true); vm.saveStaff(null, "가상", "x".repeat(65), true)
        vm.saveProfile("n1", "2026-13", "간호사"); vm.saveProfile("n1", "2026-01", "없는 직급")
        assertEquals(0, api.writes)
    }
    @Test fun renamingStaffPreservesCommaInsideQualification() {
        val staff = nurse().copy(qualifications = listOf("자격 A,B", "자격 C"))
        api.nurses = listOf(staff); open()
        vm.saveStaff(staff, "변경 이름", staff.qualifications.joinToString(", "), true)
        assertEquals(1, api.writes)
        assertEquals(AdminStaffInput("변경 이름", listOf("자격 A,B", "자격 C"), true), api.lastStaff)
    }
    @Test fun deactivatingStaffPreservesCommaInsideQualification() {
        val staff = nurse().copy(qualifications = listOf("자격 A,B"))
        api.nurses = listOf(staff); open()
        vm.saveStaff(staff, staff.displayName, "자격 A,B", false)
        assertEquals(1, api.writes)
        assertEquals(AdminStaffInput(staff.displayName, listOf("자격 A,B"), false), api.lastStaff)
    }
    @Test fun unchangedQualificationsUseOriginalItemCountForValidation() {
        val staff = nurse().copy(qualifications = (1..50).map { "자격 $it,A" })
        api.nurses = listOf(staff); open()
        vm.saveStaff(staff, "변경 이름", staff.qualifications.joinToString(", "), true)
        assertEquals(1, api.writes)
        assertEquals(staff.qualifications, api.lastStaff!!.qualifications)
    }
    @Test fun editedQualificationsCanBeReplacedAndCleared() {
        val staff = nurse().copy(qualifications = listOf("자격 A,B"))
        api.nurses = listOf(staff); open()
        vm.saveStaff(staff, staff.displayName, " 자격 C, 자격 D, 자격 C ", true)
        assertEquals(listOf("자격 C", "자격 D"), api.lastStaff!!.qualifications)
        vm.saveStaff(staff, staff.displayName, "", true)
        assertEquals(emptyList<String>(), api.lastStaff!!.qualifications)
        assertEquals(2, api.writes)
    }
    @Test fun newStaffQualificationsStillUseCommaSeparatedInput() {
        open(); vm.saveStaff(null, "가상 간호사", " 자격 A, 자격 B, 자격 A ", true)
        assertEquals(1, api.writes)
        assertEquals(listOf("자격 A", "자격 B"), api.lastStaff!!.qualifications)
    }
    @Test fun departingScreenCancelsPendingReadAndDoesNotResurrectHistory() {
        open(); val wait = CompletableDeferred<Unit>(); api.historyRead = { wait.await() }
        vm.history("n1"); vm.deactivate(); wait.complete(Unit)
        assertNull(vm.state.value.data); assertTrue(vm.state.value.history.isEmpty())
        api.historyRead = {}; vm.activate(); assertTrue(vm.state.value.editable)
    }
    @Test fun historyForPreviousNurseCannotReplaceLatestSelection() {
        open(); val wait = CompletableDeferred<Unit>(); api.historyRead = { wait.await() }
        vm.history("n1"); api.historyRead = {}; vm.history("n2"); wait.complete(Unit)
        assertEquals("n2", vm.state.value.history.single().adjustmentId)
    }
    @Test fun leavingDuringWriteRetainsUncertainOutcomeUntilFreshReload() {
        open(); val wait = CompletableDeferred<Unit>(); api.write = { wait.await() }
        vm.saveTeam(null, "가상팀", "1"); vm.deactivate(); wait.complete(Unit)
        assertTrue(vm.state.value.uncertain); vm.activate(); assertFalse(vm.state.value.editable)
        vm.acknowledge(); assertTrue(vm.state.value.editable); assertEquals(1, api.writes)
    }
    @Test fun historyOnStaleDataOffersRetryInsteadOfEndlessLoading() {
        open(); api.read = { throw ApiException(0, "NETWORK", "고정") }; vm.reload()
        vm.history("n1")
        assertFalse(vm.state.value.historyLoading)
        assertNotNull(vm.state.value.historyError)
    }
}
