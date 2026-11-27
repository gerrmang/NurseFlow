package com.nurseflow.app.feature.holiday

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nurseflow.app.core.network.*
import com.nurseflow.app.ui.theme.NurseFlowTheme
import okhttp3.Headers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.lang.reflect.Proxy
import java.time.YearMonth

/** Screen checks use a fictional API. JVM real-server test exercises actual Retrofit/Spring/H2. */
@RunWith(AndroidJUnit4::class)
class HolidaySettingsScreenUiTest {
    @get:Rule val rule = createComposeRule()
    private var writes = 0
    private var days = emptyList<HolidayDay>()
    private val month = YearMonth.now().toString()
    private var policy = HolidayPolicy(480, 480, 240, 480, null, null, 1)
    private fun <T> tagged(v: T) = Response.success(v, Headers.headersOf("ETag", "\"p1\""))
    private fun calendar() = HolidayCalendar(month, days, listOf(HolidayDay("$month-03", "PUBLIC_HOLIDAY", "가상 공휴일")), HolidaySource(false, null), HolidayTarget(month, YearMonth.parse(month).lengthOfMonth(), 9, 1, 0, 0, 10, 20, 480, 9600), 1)
    private val api = Proxy.newProxyInstance(HolidaySettingsApi::class.java.classLoader, arrayOf(HolidaySettingsApi::class.java)) { _, method, args ->
        when (method.name) {
            "calendar" -> tagged(calendar())
            "policy" -> tagged(policy)
            "saveCalendar" -> { writes++; days = (args!![3] as HolidayCalendarInput).days; tagged(calendar()) }
            "savePolicy" -> { writes++; val p = args!![2] as HolidayPolicyInput; policy = policy.copy(nightDedicatedTargetMinutes = p.nightDedicatedTargetMinutes); tagged(policy) }
            else -> error("Unexpected call")
        }
    } as HolidaySettingsApi
    private val model = HolidaySettingsViewModel(api, "w1", { listOf(MembershipDto("w1", "가상 병동", listOf("WARD_ADMIN"), true)) })
    private fun show() {
        val content: @androidx.compose.runtime.Composable () -> Unit = { NurseFlowTheme { HolidaySettingsScreen("가상 병동", model, {}, {}) } }
        rule.setContent(content)
        rule.waitUntil(5_000) { model.state.value.editable }
    }
    @Test fun calendarEditRequiresConfirmationAndShowsBothOrigins() {
        show()
        rule.onNodeWithContentDescription("$month-03 · 공휴일 · 국가 공휴일 가상 공휴일").performScrollTo().performClick()
        rule.onNodeWithText("달력에 적용").performScrollTo().performClick()
        assertEquals(0, writes)
        rule.onNodeWithText("달력 저장", useUnmergedTree = true).performScrollTo().performClick()
        rule.onNodeWithText("변경 확인").assertExists(); assertEquals(0, writes)
        rule.onNodeWithText("확인", substring = false).performClick()
        rule.waitUntil(5_000) { !model.state.value.busy }
        assertEquals(1, writes); assertEquals("WARD_REST_DAY", days.single().kind)
        rule.onNodeWithContentDescription("$month-03 · 병동 휴무 · 병동 지정  · 국가 공휴일 가상 공휴일").performScrollTo().assertExists()
    }
    @Test fun nullableDedicatedTargetAcceptsZero() {
        show()
        rule.onNodeWithText("Night 전담 월 목표 (분)").performScrollTo().performTextInput("0")
        rule.onNodeWithText("Night 전담 월 목표 (분)").performScrollTo().assertTextContains("0")
        rule.onNodeWithText("목표시간 정책 저장").performScrollTo().performClick(); assertEquals(0, writes)
        rule.onNodeWithText("확인", substring = false).performClick()
        rule.waitUntil(5_000) { !model.state.value.busy }; assertEquals(0, policy.nightDedicatedTargetMinutes)
    }
    @Test fun unconfiguredSourceDisablesRefreshAndMonthDiscardCanBeCancelled() {
        show(); rule.onNodeWithText("국가 공휴일 갱신 안내").performScrollTo().performClick()
        rule.onNodeWithText("공휴일 지금 가져오기").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithContentDescription("$month-03 · 공휴일 · 국가 공휴일 가상 공휴일").performScrollTo().performClick()
        rule.onNodeWithText("달력에 적용").performScrollTo().performClick()
        rule.onNodeWithText("월 조회").performScrollTo().performClick(); rule.onNodeWithText("취소").performClick()
        assertTrue(model.state.value.calendarDirty); assertEquals(0, writes)
    }
}
