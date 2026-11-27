package com.nurseflow.app.feature.holiday

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
import java.util.UUID

class HolidaySettingsRealServerTest {
    @Test fun actualRetrofitAndViewModelPersistAndRejectStalePolicyAndMemberAccess() = runBlocking {
        assumeTrue("Start isolated B5a OIDC/Spring fixture and opt in", System.getenv("NURSEFLOW_HOLIDAY_ANDROID_E2E") == "1")
        val base = "http://127.0.0.1:18091/"
        val suffix = UUID.randomUUID().toString()
        suspend fun login(role: String): SessionManager {
            val raw = withContext(Dispatchers.IO) { OkHttpClient().newCall(Request.Builder().url("http://127.0.0.1:18090/id-token?account=$role-$suffix").build()).execute().use { it.body!!.string() } }
            val token = Json.parseToJsonElement(raw).jsonObject.getValue("idToken").jsonPrimitive.content
            return SessionManager(ApiFactory.authApi(base), MemoryTokenStore()).also { it.loginWithKakao(token, "fictional-nonce") }
        }
        val admin = login("admin")
        val member = login("member")
        val creation = ApiFactory.wardCreationApi(base, admin)
        val hospital = creation.searchHospitals("365").items.first()
        val ward = creation.createWard(suffix, CreateWardBody(HospitalRefBody(hospital.source, hospital.externalId), "가상 휴일 Android $suffix"))
        val api = ApiFactory.holidaySettingsApi(base, admin)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val model = HolidaySettingsViewModel(api, ward.wardId, { ApiFactory.nurseFlowApi(base, admin).wards().items }, scope = scope)
        suspend fun settled() = withTimeout(20_000) { model.state.first { !it.loading && !it.busy && it.calendar != null } }
        try {
            model.activate(); settled(); model.reload("2026-11"); settled()
            assertTrue(model.state.value.editable)
            val old = model.state.value.etag
            model.editDay(HolidayDay("2026-11-02", "WARD_REST_DAY", "가상 병동 휴무")); model.editPolicy(0, "420")
            model.save("calendar"); val calendarSaved = settled()
            assertEquals(20, calendarSaved.calendar!!.target.targetDays); assertNotEquals(old, calendarSaved.etag)
            assertEquals("420", calendarSaved.policy[0]); assertTrue(calendarSaved.policyDirty)
            model.save("policy"); assertEquals(8400, settled().calendar!!.target.generalTargetMinutes)
            model.editPolicy(4, "0"); model.save("policy"); settled(); assertEquals(0, api.policy(ward.wardId).body()!!.nightDedicatedTargetMinutes)
            model.editPolicy(4, ""); model.save("policy"); settled(); assertNull(api.policy(ward.wardId).body()!!.nightDedicatedTargetMinutes)
            assertEquals(412, api.saveCalendar(ward.wardId, "2026-11", old, HolidayCalendarInput(emptyList())).code())
            model.save("refresh"); assertTrue(settled().editable); assertTrue(model.state.value.notice!!.contains("직접 입력"))
            model.deactivate(); model.activate(); assertEquals(1, settled().days.size)
            val tag = model.state.value.etag
            assertTrue(api.saveCalendar(ward.wardId, "2026-11", tag, HolidayCalendarInput(emptyList())).isSuccessful)
            model.save("policy"); val conflict = settled(); assertTrue(conflict.stale); assertTrue(conflict.notice!!.contains("다른 화면"))
            val inviteApi = ApiFactory.invitationApi(base, admin)
            val invite = inviteApi.create(ward.wardId, CreateInvitationBody("WARD_MEMBER"))
            ApiFactory.invitationApi(base, member).redeem(RedeemInvitationBody(invite.token, "2020-01"))
            val memberApi = ApiFactory.holidaySettingsApi(base, member)
            assertEquals(403, memberApi.calendar(ward.wardId, "2026-11").code()); assertEquals(403, memberApi.policy(ward.wardId).code())
        } finally { model.deactivate(); scope.cancel() }
    }
}
