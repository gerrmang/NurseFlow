package com.nurseflow.app.feature.offrequest

import com.nurseflow.app.core.network.*
import com.nurseflow.app.core.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.time.*
import java.util.UUID

/** Production Retrofit and ViewModel against Spring/new memory H2; only OIDC and people are fictional. */
class OffRequestRealServerTest {
    @Test fun nurseAdminTransitionsLimitsExceptionsPolicyAndIsolation() = runBlocking {
        assumeTrue("Start android/scripts/b8a-server.mjs", System.getenv("NURSEFLOW_B8A_E2E") == "1")
        val base = "http://127.0.0.1:18091/"
        val unique = UUID.randomUUID().toString()
        suspend fun login(role: String): SessionManager {
            val body = withContext(Dispatchers.IO) { OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:18090/id-token?account=$role-$unique").build()).execute().use { it.body!!.string() } }
            val token = Json.parseToJsonElement(body).jsonObject.getValue("idToken").jsonPrimitive.content
            return SessionManager(ApiFactory.authApi(base), MemoryTokenStore()).also { it.loginWithKakao(token, "fictional-nonce") }
        }
        val admin = login("admin"); val nurse = login("member"); val outsider = login("other")
        val creation = ApiFactory.wardCreationApi(base, admin)
        val hospital = creation.searchHospitals("B6").items.single()
        val ward = creation.createWard(unique, CreateWardBody(HospitalRefBody(hospital.source, hospital.externalId), "B8a 가상 병동"))
        val w = ward.wardId
        val staff = ApiFactory.staffAdminApi(base, admin)
        val record = staff.createStaff(w, AdminStaffInput("가상 간호사"))
        val invitation = ApiFactory.invitationApi(base, admin).create(w, CreateInvitationBody("WARD_MEMBER", record.nurseId))
        ApiFactory.invitationApi(base, nurse).redeem(RedeemInvitationBody(invitation.token, "2024-01"))
        val a = ApiFactory.offRequestApi(base, admin); val n = ApiFactory.offRequestApi(base, nurse)
        val month = YearMonth.now(ZoneId.of("Asia/Seoul")).plusMonths(2)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        fun model(api: OffRequestApi, session: SessionManager, adminRole: Boolean) = OffRequestViewModel(api, w, adminRole, { ApiFactory.nurseFlowApi(base, session).wards().items }, initialMonth = month, scope = scope)
        val nm = model(n, nurse, false); val am = model(a, admin, true)
        suspend fun loaded(vm: OffRequestViewModel) { withTimeout(10_000) { vm.state.first { !it.loading && !it.busy } }; assertTrue(vm.state.value.notice, vm.state.value.editable) }
        suspend fun code(expected: String, block: suspend () -> Unit) {
            try { apiCall { block() }; fail("Expected $expected") } catch (e: ApiException) { assertEquals(expected, e.code) }
        }
        try {
            nm.activate(); loaded(nm); assertTrue(nm.state.value.data!!.list.items.isEmpty())
            am.activate(); loaded(am); am.savePolicy("1", "15"); loaded(am)
            nm.reload(); loaded(nm); nm.submit(month.atDay(10).toString()); loaded(nm)
            val first = nm.state.value.data!!.list.items.single()
            assertEquals("NURSE", first.source)
            code("OFF_REQUEST_LIMIT_REACHED") { n.submit(w, OffRequestInput(month.atDay(11).toString())) }
            code("OFF_REQUEST_DUPLICATE") { n.submit(w, OffRequestInput(first.date)) }
            nm.decide(first.requestId, "withdraw"); loaded(nm); assertEquals("WITHDRAWN", nm.state.value.data!!.list.items.single().status)
            nm.submit(first.date); loaded(nm)
            am.reload(); loaded(am); val submitted = am.state.value.data!!.list.items.first { it.status == "SUBMITTED" }
            am.decide(submitted.requestId, "confirm"); loaded(am)
            assertEquals("CONFIRMED", am.state.value.data!!.list.items.first { it.requestId == submitted.requestId }.status)
            code("OFF_REQUEST_STATE_CONFLICT") { n.withdraw(w, submitted.requestId) }
            am.decide(submitted.requestId, "withdraw"); loaded(am)
            am.submit(month.atDay(11).toString(), record.nurseId); loaded(am)
            assertEquals("ADMIN", am.state.value.data!!.list.items.first { it.date == month.atDay(11).toString() }.source)
            val stale = a.policy(w).checkedPolicy(); am.savePolicy("2", "14"); loaded(am)
            code("STALE_POLICY_REVISION") { a.savePolicy(w, stale.etag, OffRequestPolicyInput(3, 13)).checkedPolicy() }
            code("WARD_ADMIN_REQUIRED") { n.policy(w).checkedPolicy() }
            code("WARD_ADMIN_REQUIRED") { n.confirm(w, submitted.requestId) }
            code("WARD_NOT_FOUND") { ApiFactory.offRequestApi(base, outsider).list(w, month.toString()) }
            // Current month is always after its prior-month deadline; choose a future date in it when available.
            val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
            val exception = n.submit(w, OffRequestInput(today.toString())).checked()
            assertTrue(exception.afterDeadline)
            code("OFF_REQUEST_DEADLINE_PASSED") { n.withdraw(w, exception.requestId) }
            assertEquals("REJECTED", a.reject(w, exception.requestId).checked().status)
            val userId = (nurse.state.value as SessionState.SignedIn).userId
            staff.membership(w, userId, AdminMembershipInput(active = false))
            nm.reload(); withTimeout(10_000) { nm.state.first { !it.loading && !it.busy } }
            assertTrue(nm.state.value.forbidden); assertNull(nm.state.value.data)
        } finally { nm.deactivate(); am.deactivate(); scope.cancel() }
    }
}
