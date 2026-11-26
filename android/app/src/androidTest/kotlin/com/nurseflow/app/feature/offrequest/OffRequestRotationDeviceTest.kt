package com.nurseflow.app.feature.offrequest

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nurseflow.app.AppContainer
import com.nurseflow.app.MainActivity
import com.nurseflow.app.core.network.*
import com.nurseflow.app.core.session.SessionState
import com.nurseflow.app.test.IsolatedSessionTestApplication
import com.nurseflow.app.test.TestTokenStore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.time.YearMonth
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Real root/retained ViewModel/Retrofit; hold a committed POST response across Activity recreation. */
@RunWith(AndroidJUnit4::class)
class OffRequestRotationDeviceTest {
    @get:Rule val rule = createEmptyComposeRule()
    private var previousProxy: java.net.ProxySelector? = null
    @Before fun isolateLoopback() {
        previousProxy = java.net.ProxySelector.getDefault()
        java.net.ProxySelector.setDefault(object : java.net.ProxySelector() {
            override fun select(uri: java.net.URI) = if (uri.host == "127.0.0.1") listOf(java.net.Proxy.NO_PROXY) else previousProxy?.select(uri) ?: listOf(java.net.Proxy.NO_PROXY)
            override fun connectFailed(uri: java.net.URI, address: java.net.SocketAddress, error: java.io.IOException) = Unit
        })
    }
    @After fun restoreProxy() { java.net.ProxySelector.setDefault(previousProxy) }
    @Test fun pendingSubmissionSurvivesActivityRecreation() = verify(false)
    @Test fun pendingConfirmationSurvivesActivityRecreation() = verify(true)

    private fun verify(admin: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as IsolatedSessionTestApplication
        val previous = app.container
        val ward = MembershipDto("w1", "가상 회전 병동", listOf(if (admin) "WARD_ADMIN" else "WARD_MEMBER"), true)
        val month = YearMonth.now()
        val date = month.atDay(10).toString()
        val original = OffRequest("r1", "n1", date, "SUBMITTED", "NURSE", "2026-11-01T00:00:00Z", afterDeadline = false)
        var rows = if (admin) listOf(original) else emptyList()
        val writes = AtomicInteger()
        val reads = AtomicInteger()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        MockWebServer().use { server ->
            fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    if (request.method == "POST" && path.startsWith("/api/v1/wards/w1/off-requests")) {
                        writes.incrementAndGet()
                        val row = if (admin) original.copy(status = "CONFIRMED", decidedAt = "2026-11-02T00:00:00Z") else {
                            val input = ApiJson.json.decodeFromString<OffRequestInput>(request.body.readUtf8())
                            original.copy(date = input.date)
                        }
                        rows = listOf(row) // Server commit precedes the delayed response.
                        entered.countDown()
                        check(release.await(18, TimeUnit.SECONDS)) { "Test must release pending response" }
                        return json(ApiJson.json.encodeToString(row))
                    }
                    return when (path) {
                        "/api/v1/auth/kakao/session" -> json("""{"accessToken":"fictional-off-rotation-access","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fictional-off-rotation-refresh","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"u1","displayName":"가상 회전 사용자"}}""")
                        "/api/v1/me" -> json(ApiJson.json.encodeToString(CurrentUserDto("u1", "가상 회전 사용자", listOf(ward))))
                        "/api/v1/wards" -> json(ApiJson.json.encodeToString(Items(listOf(ward))))
                        "/api/v1/wards/w1/off-requests" -> {
                            reads.incrementAndGet()
                            json(ApiJson.json.encodeToString(OffRequestList(rows, "2099-01-01T00:00:00Z", 4)))
                        }
                        "/api/v1/wards/w1/off-requests/policy" -> json(ApiJson.json.encodeToString(OffRequestPolicy(4, 15, 0))).setHeader("ETag", "\"policy-0\"")
                        "/api/v1/wards/w1/roster-eligibility" -> json(ApiJson.json.encodeToString(OffEligibility(false, 0, listOf(OffEligibleNurse("n1", "가상 간호사", true, "ELIGIBLE")))))
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            val container = AppContainer(instrumentation.targetContext, "http://127.0.0.1:${server.port}/", TestTokenStore())
            runBlocking {
                withTimeout(5_000) { container.session.state.first { it == SessionState.SignedOut } }
                container.session.loginWithKakao("fictional-id", "fictional-nonce")
            }
            app.useContainer(container)
            fun waitText(text: String) { rule.waitUntil(10_000) { rule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() } }
            try {
                ActivityScenario.launch<MainActivity>(Intent(instrumentation.targetContext, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)).use { scenario ->
                    try {
                        waitText("희망 오프")
                        rule.onNodeWithText("희망 오프").performScrollTo().performClick()
                        waitText(if (admin) "관리자 취합·판단" else "내 희망 오프")
                        if (admin) {
                            waitText("월 본인 신청 한도 4일 · 관리자 대리 입력은 한도에서 제외돼요.")
                            rule.onNodeWithTag("off-list").performScrollToNode(hasText("확정", substring = false))
                            rule.onNodeWithText("확정", substring = false).performClick()
                            rule.onNode(hasText("확정", substring = false) and hasAnyAncestor(isDialog())).performClick()
                        } else {
                            rule.waitUntil(10_000) { rule.onAllNodes(hasText("10", substring = false) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
                            rule.onNodeWithText("10", substring = false).performScrollTo().performClick()
                            rule.onNode(hasText("신청", substring = false) and hasClickAction()).performScrollTo().performClick()
                            rule.onNodeWithText("제출", substring = false).performClick()
                        }
                        assertTrue("POST must reach server before recreation", entered.await(5, TimeUnit.SECONDS))
                        val previousReads = reads.get()
                        lateinit var old: MainActivity
                        scenario.onActivity { old = it }
                        scenario.recreate()
                        scenario.onActivity { assertNotSame(old, it); assertTrue(old.isDestroyed) }
                        rule.waitForIdle()
                        assertEquals("Recreation must not restart loading while the original POST is pending", previousReads, reads.get())
                        rule.onNodeWithText("최신 목록 확인 후 계속").assertDoesNotExist()
                        release.countDown()
                        val done = if (admin) "확정 완료. 최신 목록을 불러왔어요." else "신청 완료. 최신 목록을 불러왔어요."
                        waitText(done)
                        val expected = date + if (admin) " · 확정" else " · 신청"
                        rule.onNodeWithTag("off-list").performScrollToNode(hasText(expected))
                        rule.onNodeWithText(expected).assertIsDisplayed()
                        rule.onNodeWithText("최신 목록 확인 후 계속").assertDoesNotExist()
                        assertEquals(1, writes.get())
                        assertEquals(previousReads + 1, reads.get())
                    } finally { release.countDown() }
                }
            } finally { release.countDown(); app.useContainer(previous) }
        }
    }
}
