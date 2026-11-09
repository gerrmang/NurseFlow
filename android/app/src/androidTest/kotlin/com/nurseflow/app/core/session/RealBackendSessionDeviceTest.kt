package com.nurseflow.app.core.session

import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nurseflow.app.AppContainer
import com.nurseflow.app.MainActivity
import com.nurseflow.app.core.network.ApiFactory
import com.nurseflow.app.ui.NurseFlowRoot
import com.nurseflow.app.ui.theme.NurseFlowTheme
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android UI + production HTTP/401 handling + opt-in Spring server; fictional OIDC only. */
@RunWith(AndroidJUnit4::class)
class RealBackendSessionDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val http = OkHttpClient()
    private val backend = "http://127.0.0.1:18081/"
    private val fixtures = "http://127.0.0.1:18082/"

    @Test fun rolesAccountSwitchAndExpiredAccessRefresh() = runBlocking {
        assumeTrue("Opt in with -e backendE2e true after starting the fixture server",
            InstrumentationRegistry.getArguments().getString("backendE2e") == "true")
        // Never read, replace or clear the developer's real Keystore session.
        val store = object : TokenStore {
            var token: String? = null
            override suspend fun read() = token
            override suspend fun write(refreshToken: String) { token = refreshToken }
            override suspend fun clear() { token = null }
        }
        val container = AppContainer(instrumentation.targetContext, backend, store)
        val activity = instrumentation.startActivitySync(Intent(instrumentation.targetContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var passed = false
        try {
            awaitState { container.session.state.value == SessionState.SignedOut }
            instrumentation.runOnMainSync {
                activity.setContent { NurseFlowTheme { NurseFlowRoot(container) } }
            }
            waitForText("카카오 로그인")
            val admin = getJson(fixtures + "identity?account=admin")
            container.session.loginWithKakao(admin.getString("idToken"), admin.getString("nonce"))
            waitForText("가상 admin 병동")
            waitForText("관리자")
            assertFalse(hasText("가상 member 병동"))
            val oldAccess = container.session.accessToken!!
            val oldRefresh = store.read()
            val before = container.session.state.value

            // Production JWT validator allows a 60-second clock skew. Wait beyond exp + skew;
            // do not substitute a fake 401 or weaken the server validator for this check.
            SystemClock.sleep(64_000)
            http.newCall(Request.Builder().url(backend + "api/v1/me")
                .header("Authorization", "Bearer $oldAccess").build()).execute().use {
                assertEquals("Expired access must be rejected by the real server", 401, it.code)
            }
            val me = container.api.me()
            assertEquals("가상 admin", me.displayName)
            assertTrue("401 must replace access token", oldAccess != container.session.accessToken)
            assertTrue("Refresh token must rotate", oldRefresh != store.read())
            assertEquals("Rotation must preserve screen identity", before, container.session.state.value)
            assertEquals("Exactly one server-side refresh", 1, getJson(fixtures + "stats").getInt("rotated"))
            clickText("새로고침")
            waitForText("가상 admin 병동")
            waitForText("관리자")

            // Exercise NurseFlow logout without logging out the developer's real Kakao SDK account.
            container.session.logout()
            waitForText("카카오 로그인")
            assertNull(container.session.accessToken)
            assertNull(store.read())
            assertFalse(hasText("가상 admin 병동"))
            assertFalse(hasText("가상 admin님, 안녕하세요."))
            assertTrue("Logout must revoke the server refresh family", getJson(fixtures + "stats").getInt("revoked") >= 1)
            val restarted = SessionManager(ApiFactory.authApi(backend), store)
            restarted.restore()
            assertEquals(SessionState.SignedOut, restarted.state.value)

            val member = getJson(fixtures + "identity?account=member")
            container.session.loginWithKakao(member.getString("idToken"), member.getString("nonce"))
            waitForText("가상 member 병동")
            waitForText("간호사")
            assertFalse(hasText("가상 admin 병동"))
            assertFalse(hasText("관리자"))
            assertFalse(hasText("가상 admin님, 안녕하세요."))
            assertEquals(listOf("WARD_MEMBER"), container.api.wards().items.single().roles)
            container.session.logout()
            waitForText("카카오 로그인")
            assertFalse(hasText("가상 member 병동"))
            passed = true
        } finally {
            try {
                container.session.logout()
                instrumentation.runOnMainSync { activity.finish() }
            } finally {
                http.newCall(Request.Builder().url(fixtures + "finish?passed=$passed").build()).execute().close()
            }
        }
    }

    private fun getJson(url: String): JSONObject = http.newCall(Request.Builder().url(url).build()).execute().use {
        check(it.code == 200) { "Fixture HTTP failure: ${it.code}" }
        JSONObject(it.body!!.string())
    }

    private fun findText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.text?.toString() == text) return node
        for (i in 0 until node.childCount) findText(node.getChild(i), text)?.let { return it }
        return null
    }

    private fun hasText(text: String) = findText(instrumentation.uiAutomation.rootInActiveWindow, text) != null
    private fun awaitState(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(100)
        }
        fail("Device state did not reach the expected value")
    }
    private fun waitForText(text: String) = awaitState { hasText(text) }
    private fun clickText(text: String) {
        var node = findText(instrumentation.uiAutomation.rootInActiveWindow, text)
        while (node != null && !node.isClickable) node = node.parent
        assertTrue("Expected button: $text", node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }
}
