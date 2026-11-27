package com.nurseflow.app.feature.holiday

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nurseflow.app.core.network.*
import okhttp3.Headers
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Response
import java.time.YearMonth
import java.util.concurrent.atomic.AtomicInteger

/** Actual Activity recreation and retained production ViewModel; all API responses are fictional. */
@RunWith(AndroidJUnit4::class)
class HolidaySettingsRotationTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun recreationKeepsPolicyInputWithoutExtraReads() {
        val reads = AtomicInteger()
        val month = YearMonth.now().toString()
        fun <T> tagged(value: T) = Response.success(value, Headers.headersOf("ETag", "\"p1\""))
        val api = object : HolidaySettingsApi {
            override suspend fun calendar(ward: String, month: String): Response<HolidayCalendar> {
                reads.incrementAndGet()
                return tagged(HolidayCalendar(month, emptyList(), emptyList(), HolidaySource(false, null), HolidayTarget(month, YearMonth.parse(month).lengthOfMonth(), 9, 0, 0, 0, 9, 21, 480, 10080), 1))
            }
            override suspend fun policy(ward: String) = tagged(HolidayPolicy(480, 480, 240, 480, null, null, 1))
            override suspend fun saveCalendar(ward: String, month: String, tag: String, input: HolidayCalendarInput): Response<HolidayCalendar> = error("Unexpected write")
            override suspend fun savePolicy(ward: String, tag: String, input: HolidayPolicyInput): Response<HolidayPolicy> = error("Unexpected write")
            override suspend fun refresh(ward: String, month: String): Response<HolidayCalendar> = error("Unexpected refresh")
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            HolidaySettingsTestActivity.createModel = { HolidaySettingsViewModel(api, "w1", { listOf(MembershipDto("w1", "가상 회전 병동", listOf("WARD_ADMIN"), true)) }) }
        }
        try {
            ActivityScenario.launch(HolidaySettingsTestActivity::class.java).use { scenario ->
                rule.waitUntil(10_000) { rule.onAllNodesWithText("하루 기준시간 (분)").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("하루 기준시간 (분)").performScrollTo().performTextReplacement("420")
                val before = reads.get(); scenario.recreate()
                rule.onNodeWithText("하루 기준시간 (분)").performScrollTo().assertTextContains("420")
                assertEquals(before, reads.get())
                rule.onNodeWithText("대상 월 (YYYY-MM)").performScrollTo().assertTextContains(month)
                rule.onNodeWithText("뒤로", substring = false).performScrollTo().performClick()
                rule.onNodeWithText("취소").performClick()
                rule.onNodeWithText("하루 기준시간 (분)").performScrollTo().assertTextContains("420")
            }
        } finally { HolidaySettingsTestActivity.createModel = null }
    }
}
