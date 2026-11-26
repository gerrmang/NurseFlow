package com.nurseflow.app.feature.schedule

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nurseflow.app.core.network.*
import com.nurseflow.app.test.TestTokenStore
import com.nurseflow.app.core.session.SessionManager
import com.nurseflow.app.ui.theme.NurseFlowTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** Opt-in emulator -> host loopback Spring + isolated H2 + fictional OIDC. No real credentials. */
@RunWith(AndroidJUnit4::class)
class ScheduleSettingsRealServerUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun fourMenusSaveAndReloadAgainstRealServer() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("b7aRealServer") == "1")
        val base = "http://10.0.2.2:18091/"
        val suffix = UUID.randomUUID().toString()
        val session = SessionManager(ApiFactory.authApi(base), TestTokenStore())
        val (ward, nurse) = runBlocking {
            val tokenJson = OkHttpClient().newCall(Request.Builder().url("http://10.0.2.2:18090/id-token?account=admin-$suffix").build()).execute().use { it.body!!.string() }
            session.loginWithKakao(Json.parseToJsonElement(tokenJson).jsonObject.getValue("idToken").jsonPrimitive.content, "fictional-nonce")
            val create = ApiFactory.wardCreationApi(base, session)
            val hospital = create.searchHospitals("365").items.first()
            val ward = create.createWard(UUID.randomUUID().toString(), CreateWardBody(HospitalRefBody(hospital.source, hospital.externalId), "B7a 화면 가상 병동 $suffix"))
            val staffApi = ApiFactory.staffAdminApi(base, session)
            val team = staffApi.createTeam(ward.wardId, AdminTeamInput("가상 A팀", 1))
            val nurse = staffApi.createStaff(ward.wardId, AdminStaffInput("가상 간호사"))
            staffApi.assignTeam(ward.wardId, nurse.nurseId, AdminTeamAssignment(team.teamId))
            staffApi.settings(ward.wardId, nurse.nurseId, AdminRosterSettings(true, 4))
            val api = ApiFactory.scheduleSettingsApi(base, session)
            val tag = requireNotNull(api.shifts(ward.wardId).headers()["ETag"])
            assertTrue(api.createShift(ward.wardId, tag, UUID.randomUUID().toString(), ScheduleShiftInput("D", "가상 Day", "DAY", "07:00", "15:00", false, 30, 450, 450)).isSuccessful)
            ward to nurse
        }
        val api = ApiFactory.scheduleSettingsApi(base, session)
        val model = ScheduleSettingsViewModel(api, ward.wardId, { ApiFactory.nurseFlowApi(base, session).wards().items })
        model.setMonth("2026-11")
        rule.setContent { NurseFlowTheme { ScheduleSettingsScreen("가상 병동", model, {}, {}) } }
        fun ready() { rule.waitUntil(20_000) { model.state.value.editable }; rule.waitForIdle() }
        fun choose(label: String, option: String) {
            rule.onNodeWithText(label, substring = false).performScrollTo().performClick()
            rule.onNodeWithText(option, substring = false).performClick()
        }
        ready()
        choose("근무 유형: 새 근무 유형", "D · 가상 Day")
        rule.onNodeWithText("월 목표 환산 시간 (분)").performScrollTo().performTextReplacement("420")
        rule.onNodeWithText("변경 영향 확인").performScrollTo().performClick()
        rule.waitUntil(10_000) { model.state.value.preview != null }
        rule.onNodeWithText("영향 확인 후 저장").performScrollTo().performClick()
        ready()
        runBlocking { assertEquals(420, api.shifts(ward.wardId).body()!!.items.single().targetCreditMinutes) }
        choose("설정 항목: 근무 유형", "근무 형태")
        choose("간호사: 선택해 주세요", "가상 간호사")
        rule.waitUntil(10_000) { model.state.value.pattern != null }
        choose("근무 형태: 일반", "N 전담")
        rule.onNodeWithText("근무 형태 저장").performScrollTo().performClick()
        ready()
        runBlocking { assertEquals("NIGHT_ONLY", api.pattern(ward.wardId, nurse.nurseId).body()!!.workPattern) }
        choose("설정 항목: 근무 형태", "팀별 필요 인원")
        rule.onNodeWithText("선택한 월 초안 만들기").performScrollTo().performClick()
        ready()
        rule.onNodeWithText("날짜 (YYYY-MM-DD)").performScrollTo().performTextReplacement("2026-11-05")
        choose("팀: 팀 미지정", "가상 A팀")
        choose("근무: 선택해 주세요", "D")
        rule.onNodeWithText("필요 인원 (0~100)").performScrollTo().performTextReplacement("2")
        rule.onNodeWithText("새 수요 목록에 추가").performScrollTo().performClick()
        rule.onNode(isToggleable()).performScrollTo().performClick()
        rule.onNodeWithText("수요 전체 교체", substring = false).performScrollTo().performClick()
        rule.onNodeWithText("전체 교체 확인").performClick()
        ready()
        assertEquals(2, model.state.value.data!!.draft!!.value.staffing.required)
        assertEquals(1, model.state.value.data!!.draft!!.value.slots!!.count { it.seniorRequired })
        choose("설정 항목: 팀별 필요 인원", "승인 부재")
        choose("간호사: 선택해 주세요", "가상 간호사")
        choose("구분: 승인 VY", "승인 반차")
        rule.onNodeWithText("시작 (YYYY-MM-DDTHH:mm)").performScrollTo().performTextReplacement("2026-11-06T09:00")
        rule.onNodeWithText("종료 (YYYY-MM-DDTHH:mm)").performScrollTo().performTextReplacement("2026-11-06T13:00")
        rule.onNodeWithText("승인 부재 저장").performScrollTo().performClick()
        ready()
        assertEquals("2026-11-06T00:00:00Z", model.state.value.data!!.absences.single().startsAt)
        assertEquals("APPROVED_HALF_DAY", model.state.value.data!!.absences.single().kind)
        rule.onNodeWithText("새로고침").performScrollTo().performClick()
        ready(); assertEquals(1, model.state.value.data!!.absences.size)
        rule.runOnIdle { model.deactivate() }
    }
}
