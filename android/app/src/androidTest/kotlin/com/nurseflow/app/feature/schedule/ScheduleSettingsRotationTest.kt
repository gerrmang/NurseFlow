package com.nurseflow.app.feature.schedule

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nurseflow.app.AppContainer
import com.nurseflow.app.MainActivity
import com.nurseflow.app.test.IsolatedSessionTestApplication
import com.nurseflow.app.test.TestTokenStore
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Real Activity/root/retained ViewModel and saved Compose form, fictional HTTP/session only. */
@RunWith(AndroidJUnit4::class)
class ScheduleSettingsRotationTest {
    @get:Rule val rule = createEmptyComposeRule()
    @Test fun recreationPreservesInputAndBackReentryReloadsServer() {
        val shiftsRead = AtomicInteger()
        val wards = """{"items":[{"wardId":"w1","wardName":"가상 회전 병동","roles":["WARD_ADMIN"],"canViewWardCalendar":true}]}"""
        MockWebServer().use { server ->
            fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/api/v1/auth/session/refresh" -> json("""{"accessToken":"fictional-access","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fictional-refresh","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"fictional-u","displayName":"가상 사용자"}}""")
                    request.path == "/api/v1/me" -> json("""{"userId":"fictional-u","displayName":"가상 사용자","memberships":[]}""")
                    request.path == "/api/v1/wards" -> json(wards)
                    request.path == "/api/v1/wards/w1/shift-types?includeInactive=true" -> { shiftsRead.incrementAndGet(); json("""{"items":[],"policyRevision":0}""").setHeader("ETag", "\"0\"") }
                    request.path?.startsWith("/api/v1/wards/w1/") == true -> json("""{"items":[]}""")
                    else -> MockResponse().setResponseCode(404)
                }
            }
            server.start()
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val store = TestTokenStore()
            runBlocking { store.write("fictional-refresh") }
            val baseUrl = server.url("/").toString()
            instrumentation.runOnMainSync {
                val application = instrumentation.targetContext.applicationContext as IsolatedSessionTestApplication
                application.useContainer(AppContainer(application, baseUrl, store))
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                rule.waitUntil(15_000) { rule.onAllNodesWithText("근무 설정").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("근무 설정", substring = false).performScrollTo().performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithText("표시 이름").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("표시 이름").performScrollTo().performTextReplacement("가상 회전 유형")
                val readsBefore = shiftsRead.get()
                scenario.recreate()
                rule.onNodeWithText("표시 이름").performScrollTo().assertTextContains("가상 회전 유형")
                assertEquals(readsBefore, shiftsRead.get())
                rule.onNodeWithText("뒤로", substring = false).performScrollTo().performClick()
                rule.waitUntil(10_000) { rule.onAllNodesWithText("근무 설정", substring = false).fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("근무 설정", substring = false).performScrollTo().performClick()
                rule.waitUntil(10_000) { shiftsRead.get() > readsBefore }
                rule.waitUntil(10_000) { rule.onAllNodesWithText("표시 이름").fetchSemanticsNodes().isNotEmpty() }
                rule.onNodeWithText("표시 이름").performScrollTo().assertTextContains("표시 이름")
                assertTrue(rule.onAllNodesWithText("가상 회전 유형", substring = false).fetchSemanticsNodes().isEmpty())
            }
        }
    }
}
