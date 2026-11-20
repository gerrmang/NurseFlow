package com.nurseflow.app.feature.staff

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import com.nurseflow.app.AppContainer
import com.nurseflow.app.MainActivity
import com.nurseflow.app.core.network.*
import com.nurseflow.app.test.IsolatedSessionTestApplication
import com.nurseflow.app.test.TestTokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import com.nurseflow.app.core.session.SessionState
import kotlinx.serialization.encodeToString
import okhttp3.mockwebserver.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.runner.RunWith

/** Real root/navigation/retained ViewModels with fictional HTTP, isolated from developer credentials. */
@RunWith(AndroidJUnit4::class)
class StaffAdminNavigationDeviceTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var originalProxy: java.net.ProxySelector? = null
    @Before fun isolateLoopbackFixtureFromDeviceProxy() {
        originalProxy = java.net.ProxySelector.getDefault()
        java.net.ProxySelector.setDefault(object : java.net.ProxySelector() {
            override fun select(uri: java.net.URI): List<java.net.Proxy> =
                if (uri.host == "127.0.0.1") listOf(java.net.Proxy.NO_PROXY)
                else originalProxy?.select(uri) ?: listOf(java.net.Proxy.NO_PROXY)
            override fun connectFailed(uri: java.net.URI, address: java.net.SocketAddress, error: java.io.IOException) = Unit
        })
    }
    @After fun restoreProcessProxy() { java.net.ProxySelector.setDefault(originalProxy) }
    @Test fun managementEntryAndDraftSurviveActivityRecreation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as IsolatedSessionTestApplication
        val previous = app.container
        val team = AdminTeam("t1", "가상 A팀", 1)
        val staff = (1..2).map { AdminStaff("n$it", "가상 n$it", emptyList(), true, false, AdminCareerProfile("MISSING"), team, it, false) }
        val ward = MembershipDto("w1", "가상 병동", listOf("WARD_ADMIN"), true)
        var writes = 0
        MockWebServer().use { server ->
            fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.method != "GET" && request.path != "/api/v1/auth/kakao/session") writes++
                    return when (request.path) {
                        "/api/v1/auth/kakao/session" -> json("""{"accessToken":"fictional-navigation-access","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fictional-navigation-refresh","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"u1","displayName":"가상 관리자"}}""")
                        "/api/v1/me" -> json(ApiJson.json.encodeToString(CurrentUserDto("u1", "가상 관리자", listOf(ward))))
                        "/api/v1/wards" -> json(ApiJson.json.encodeToString(Items(listOf(ward))))
                        "/api/v1/wards/w1/teams" -> json(ApiJson.json.encodeToString(Items(listOf(team))))
                        "/api/v1/wards/w1/staff?includeInactive=true" -> json(ApiJson.json.encodeToString(Items(staff)))
                        "/api/v1/wards/w1/members" -> json(ApiJson.json.encodeToString(Items(listOf(AdminMember("u1", "가상 관리자", listOf("WARD_ADMIN"), true)))))
                        "/api/v1/wards/w1/career-policy" -> json(ApiJson.json.encodeToString(AdminCareerPolicy(listOf(12,36,72,120), listOf("간호사"), true)))
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            val baseUrl = "http://127.0.0.1:${server.port}/"
            val container = AppContainer(instrumentation.targetContext, baseUrl, TestTokenStore())
            runBlocking {
                withTimeout(5_000) { container.session.state.first { it == SessionState.SignedOut } }
                container.session.loginWithKakao("fictional-id", "fictional-nonce")
            }
            app.useContainer(container)
            try {
                ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)).use { scenario ->
                    rule.waitUntil(10_000) { rule.onAllNodesWithText("팀·간호사·구성원 관리").fetchSemanticsNodes().isNotEmpty() }
                    rule.onNodeWithText("팀·간호사·구성원 관리").performScrollTo().performClick()
                    rule.waitUntil(10_000) { rule.onAllNodesWithText("1. 가상 n1").fetchSemanticsNodes().isNotEmpty() }
                    rule.onNodeWithContentDescription("가상 n1 아래로").performScrollTo().performClick()
                    rule.onNodeWithText("팀 추가").performScrollTo().performClick()
                    rule.onNodeWithText("팀 이름 (1~50자)").performTextInput("가상 새 팀")
                    scenario.recreate()
                    rule.waitUntil(10_000) { rule.onAllNodesWithText("가상 새 팀").fetchSemanticsNodes().isNotEmpty() }
                    rule.onNodeWithText("가상 새 팀").assertExists()
                    rule.onNodeWithText("취소", substring = false).performClick()
                    rule.onNodeWithText("1. 가상 n2").performScrollTo().assertExists()
                    assertEquals(0, writes)
                }
            } finally { app.useContainer(previous) }
        }
    }
}
