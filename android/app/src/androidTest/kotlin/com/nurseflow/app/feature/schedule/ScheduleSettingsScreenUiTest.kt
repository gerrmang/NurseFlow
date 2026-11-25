package com.nurseflow.app.feature.schedule

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nurseflow.app.core.network.*
import com.nurseflow.app.ui.theme.NurseFlowTheme
import okhttp3.Headers
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy

/** Fictional API UI checks. Real server coverage lives in ScheduleSettingsRealServerTest. */
@RunWith(AndroidJUnit4::class)
class ScheduleSettingsScreenUiTest {
    @get:Rule val rule = createComposeRule()
    private var writes = 0
    private var captured: Any? = null
    private var patternFail = false
    private var revision = 1
    private val originalStart = "2026-11-05T00:00:30.123456789Z"
    private val originalEnd = "2026-11-05T04:00:30.987654321Z"
    private val shift = ScheduleShift("s1", "D", "가상 Day", "DAY", "07:00", "15:00", false, 0, 480, 480, 480, true)
    private fun <T> tagged(value: T) = Response.success(value, Headers.headersOf("ETag", "\"$revision\""))
    private fun roster() = ScheduleRoster("r1", "w1", "2026-11", "DRAFT", revision, "Asia/Seoul", ScheduleStaffing(1, 0, 1, 0, 1), listOf(ScheduleSlot("slot", "2026-11-01", "s1", "D", "UNFILLED", "t1", true)))
    private val api = Proxy.newProxyInstance(ScheduleSettingsApi::class.java.classLoader, arrayOf(ScheduleSettingsApi::class.java)) { _, method, args ->
        when (method.name) {
            "shifts" -> tagged(ScheduleShifts(listOf(shift), 1))
            "teams" -> Response.success(Items(listOf(AdminTeam("t1", "가상 A팀", 1))))
            "staff" -> Response.success(Items(listOf(AdminStaff("n1", "가상 간호사", emptyList(), true, false, AdminCareerProfile("MISSING"), seniorEligible = true))))
            "rosters" -> Response.success(Items(listOf(roster())))
            "roster" -> tagged(roster())
            "absences" -> Response.success(Items(listOf(ScheduleAbsence("a1", "n1", "APPROVED_HALF_DAY", originalStart, originalEnd))))
            "pattern" -> if (patternFail) Response.error<SchedulePattern>(503, "".toResponseBody()) else Response.success(SchedulePattern("GENERAL"))
            "preview" -> Response.success(SchedulePreview(shift, 1, true, true, listOf(ScheduleAffectedDraft("2026-11", 1, 0)), 0))
            else -> {
                writes++; revision++; captured = args!!.dropLast(1).last()
                when (method.name) {
                    "createShift", "updateShift" -> tagged(shift)
                    "requirements" -> tagged(roster())
                    "updateAbsence" -> { val i = captured as ScheduleAbsenceInput; tagged(ScheduleAbsence("a1", i.nurseId, i.kind, i.startsAt, i.endsAt)) }
                    else -> error("Unexpected ${method.name}")
                }
            }
        }
    } as ScheduleSettingsApi
    private fun show() {
        val model = ScheduleSettingsViewModel(api, "w1", { listOf(MembershipDto("w1", "가상 병동", listOf("WARD_ADMIN"), true)) })
        model.setMonth("2026-11")
        rule.setContent { NurseFlowTheme { ScheduleSettingsScreen("가상 병동", model, {}, {}) } }
        rule.onNodeWithText("가상 병동 · 근무 설정").assertExists()
    }
    private fun tab(name: String) {
        rule.onNodeWithText("설정 항목: 근무 유형").performScrollTo().performClick()
        rule.onNodeWithText(name, substring = false).performClick()
    }
    @Test fun editingShiftRequiresPreviewBeforeSaving() {
        show()
        rule.onNodeWithText("근무 유형: 새 근무 유형").performScrollTo().performClick()
        rule.onNodeWithText("D · 가상 Day").performClick()
        rule.onNodeWithText("월 목표 환산 시간 (분)").performScrollTo().performTextReplacement("420")
        rule.onNodeWithText("영향 확인 후 저장").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("변경 영향 확인").performScrollTo().performClick()
        rule.onNodeWithText("영향 확인 후 저장").performScrollTo().assertIsEnabled().performClick()
        rule.runOnIdle { assertEquals(1, writes); assertEquals(420, (captured as ScheduleShiftInput).targetCreditMinutes) }
    }
    @Test fun clearingDemandRequiresCheckboxAndConfirmation() {
        show(); tab("팀별 필요 인원")
        rule.onNodeWithText("수요 전체 교체", substring = false).performScrollTo().assertIsNotEnabled()
        rule.onNode(isToggleable()).performScrollTo().performClick()
        rule.onNodeWithText("수요 전체 교체", substring = false).performClick()
        rule.runOnIdle { assertEquals(0, writes) }
        rule.onNodeWithText("전체 교체 확인").performClick()
        rule.runOnIdle { assertEquals(1, writes); assertTrue((captured as ScheduleRequirements).items.isEmpty()) }
    }
    @Test fun editingOnlyAbsenceKindPreservesOriginalNanoseconds() {
        show(); tab("승인 부재")
        rule.onNodeWithText("승인 부재: 새 부재").performScrollTo().performClick()
        rule.onNodeWithText("가상 간호사 · 2026-11-05T09:00 · 반차").performClick()
        rule.onNodeWithText("구분: 승인 반차").performScrollTo().performClick()
        rule.onNodeWithText("승인 VY", substring = false).performClick()
        rule.onNodeWithText("승인 부재 저장").performScrollTo().performClick()
        rule.runOnIdle {
            val i = captured as ScheduleAbsenceInput
            assertEquals("APPROVED_LEAVE", i.kind); assertEquals(originalStart, i.startsAt); assertEquals(originalEnd, i.endsAt)
        }
    }
    @Test fun unknownPatternCannotBeSavedAsGeneral() {
        patternFail = true; show(); tab("근무 형태")
        rule.onNodeWithText("간호사: 선택해 주세요").performScrollTo().performClick()
        rule.onNodeWithText("가상 간호사", substring = false).performClick()
        rule.onNodeWithText("근무 형태 저장").assertDoesNotExist()
        rule.onNodeWithText("근무 형태 다시 조회").performScrollTo().assertIsEnabled()
        rule.runOnIdle { assertEquals(0, writes); patternFail = false }
        rule.onNodeWithText("근무 형태 다시 조회").performClick()
        rule.onNodeWithText("현재 서버 값: 일반").assertExists()
    }
}
