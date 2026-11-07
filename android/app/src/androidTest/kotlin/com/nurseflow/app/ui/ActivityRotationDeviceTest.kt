package com.nurseflow.app.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.auth.AuthCodeHandlerActivity
import com.kakao.sdk.common.KakaoSdk
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.nurseflow.app.AppContainer
import com.nurseflow.app.MainActivity
import com.nurseflow.app.core.network.*
import com.nurseflow.app.core.session.SessionState
import com.nurseflow.app.core.session.TokenStore
import com.nurseflow.app.feature.auth.KakaoLoginClient
import com.nurseflow.app.feature.auth.KakaoLoginSdk
import com.nurseflow.app.feature.auth.AndroidKakaoLoginSdk
import com.nurseflow.app.test.IsolatedSessionTestApplication
import com.nurseflow.app.test.TestTokenStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.decodeFromString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real MainActivity/root/ViewModels/HTTP; only the external SDK callback source is fake. */
@RunWith(AndroidJUnit4::class)
class ActivityRotationDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val server = MockWebServer()
    private val loginCalls = AtomicInteger()
    private val loginBody = AtomicReference<KakaoLoginBody>()
    private val sdk = CallbackSdk()
    private val store = TestTokenStore()
    private lateinit var container: AppContainer
    private var scenario: ActivityScenario<MainActivity>? = null
    private val loginText = "카카오 로그인"
    private val retryText = "세션 복원 재시도"

    @Before fun serveFictionalSession() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/v1/auth/kakao/session" -> {
                    loginCalls.incrementAndGet()
                    loginBody.set(ApiJson.json.decodeFromString(request.body.readUtf8()))
                    json("""{"accessToken":"fake-rotation-access","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fake-rotation-refresh","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"fake-rotation-user","displayName":"가상 회전 사용자"}}""")
                }
                "/api/v1/me" -> json("""{"userId":"fake-rotation-user","displayName":"가상 회전 사용자","memberships":[]}""")
                "/api/v1/wards" -> json("""{"items":[]}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        server.start()
    }

    @After fun closeScreenAndServer() {
        scenario?.close()
        server.shutdown()
    }

    @Test fun kakaoTalkSuccessAfterActivityRecreationCompletesOriginalLogin() {
        verifyLoginSuccess(talkAvailable = true)
    }

    @Test fun kakaoAccountSuccessAfterActivityRecreationCompletesOriginalLogin() {
        verifyLoginSuccess(talkAvailable = false)
    }

    private fun verifyLoginSuccess(talkAvailable: Boolean) {
        launch(talkAvailable)
        val pending = beginLogin()
        recreateActivity()
        assertFalse(hasText(loginText)) // The original login is still loading.
        assertSame(pending, sdk.pending)
        assertEquals(1, sdk.talkCalls + sdk.accountCalls)
        assertEquals(SessionState.SignedOut, container.session.state.value)
        complete(pending, token())
        assertLoginCompleted(pending.nonce)
        assertEquals(if (talkAvailable) 1 else 0, sdk.talkCalls)
        assertEquals(if (talkAvailable) 0 else 1, sdk.accountCalls)
    }

    @Test fun kakaoTalkCancellationAfterRecreationAllowsAnotherLogin() {
        launch(talkAvailable = true)
        val pending = beginLogin()
        recreateActivity()
        complete(pending, error = ClientError(ClientErrorCause.Cancelled, "fake user cancellation"))
        awaitText(loginText)
        assertEquals(SessionState.SignedOut, container.session.state.value)
        assertEquals(0, loginCalls.get())
        assertEquals(0, sdk.accountCalls) // Cancellation must not fall back to account login.
        val next = beginLogin()
        assertNotEquals(pending.nonce, next.nonce)
        complete(next, token())
        assertLoginCompleted(next.nonce)
    }

    @Test fun kakaoTalkFailureAfterRecreationPreservesNonceAndCompletesFallback() {
        launch(talkAvailable = true)
        val talk = beginLogin()
        val original = activity(talk.context)
        recreateActivity()
        assertTrue(original.isDestroyed)
        complete(talk, error = IOException("fake KakaoTalk failure after recreation"))
        await { sdk.accountCalls == 1 }
        val account = requireNotNull(sdk.pending)
        assertNotSame(talk, account)
        assertEquals(talk.nonce, account.nonce)
        assertEquals(1, sdk.talkCalls)
        complete(account, token())
        assertLoginCompleted(talk.nonce)
    }

    @Test fun kakaoTalkFailureAfterRecreationCanStartRealSdkAccountActivity() {
        // Test the SDK's real launch path, but block external browser/chooser intents.
        // No actual account, authorization request or token exchange is needed.
        val externalBrowser = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                if (intent.action == Intent.ACTION_VIEW || intent.action == Intent.ACTION_CHOOSER)
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                else null
        }
        instrumentation.addMonitor(externalBrowser)
        val accountMonitor = instrumentation.addMonitor(AuthCodeHandlerActivity::class.java.name, null, false)
        var accountActivity: Activity? = null
        try {
            instrumentation.runOnMainSync {
                KakaoSdk.init(instrumentation.targetContext.applicationContext, "00000000000000000000000000000001")
            }
            sdk.realAccountLogin = true
            launch(talkAvailable = true)
            val talk = beginLogin()
            val original = activity(talk.context)
            recreateActivity()
            assertTrue(original.isDestroyed)
            complete(talk, error = IOException("fake KakaoTalk failure after recreation"))
            await { sdk.accountCalls == 1 }
            val account = requireNotNull(sdk.pending)
            assertEquals(talk.nonce, account.nonce)
            // Diagnostic precondition: exercise the SDK with the destroyed context in question.
            assertSame(original, activity(account.context))
            accountActivity = instrumentation.waitForMonitorWithTimeout(accountMonitor, 10_000)
            assertNotNull("The real SDK must start its account Activity from this context", accountActivity)
            assertEquals(AuthCodeHandlerActivity::class.java, accountActivity!!.javaClass)
            assertTrue(accountActivity!!.intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
            assertEquals(0, loginCalls.get())
        } finally {
            accountActivity?.let { launched -> instrumentation.runOnMainSync { launched.finish() } }
            instrumentation.removeMonitor(accountMonitor)
            instrumentation.removeMonitor(externalBrowser)
        }
    }

    @Test fun kakaoAccountErrorAfterRecreationShowsErrorAndAllowsAnotherLogin() {
        launch()
        val pending = beginLogin()
        recreateActivity()
        complete(pending, error = IOException("fake SDK failure"))
        awaitText(loginText)
        assertTrue(hasText("카카오 로그인에 실패했어요.", substring = true))
        assertEquals(SessionState.SignedOut, container.session.state.value)
        assertEquals(0, loginCalls.get())
        val next = beginLogin()
        complete(next, token())
        assertLoginCompleted(next.nonce)
    }

    @Test fun restoreRetrySuccessAfterActivityRecreationLeavesLoading() {
        verifyRestoreRetry(failRetry = false)
    }

    @Test fun restoreRetryFailureAfterActivityRecreationAllowsAnotherRetry() {
        verifyRestoreRetry(failRetry = true)
    }

    private fun verifyRestoreRetry(failRetry: Boolean) {
        val entered = CompletableDeferred<Unit>()
        val response = CompletableDeferred<Unit>()
        val retryStore = object : TokenStore {
            var reads = 0
            override suspend fun read(): String? {
                when (++reads) {
                    1 -> throw IOException("fake initial restore failure")
                    2 -> {
                        entered.complete(Unit)
                        response.await()
                        if (failRetry) throw IOException("fake retry failure")
                    }
                }
                return null
            }
            override suspend fun write(refreshToken: String) = error("No credentials expected")
            override suspend fun clear() = Unit
        }
        launch(tokenStore = retryStore)
        awaitText(retryText)
        clickText(retryText)
        await { entered.isCompleted }
        try {
            recreateActivity()
            assertEquals(SessionState.Restoring, container.session.state.value)
        } finally { response.complete(Unit) }
        if (failRetry) {
            awaitText(retryText)
            assertTrue(container.session.state.value is SessionState.RestoreFailed)
            clickText(retryText)
        }
        awaitText(loginText)
        assertEquals(SessionState.SignedOut, container.session.state.value)
        assertEquals(0, loginCalls.get())
        assertEquals(0, sdk.talkCalls + sdk.accountCalls)
    }

    private fun launch(talkAvailable: Boolean = false, tokenStore: TokenStore = store) {
        sdk.talkAvailable = talkAvailable
        val baseUrl = server.url("/").toString()
        instrumentation.runOnMainSync {
            val application = instrumentation.targetContext.applicationContext as IsolatedSessionTestApplication
            container = AppContainer(application, baseUrl, tokenStore, KakaoLoginClient(sdk))
            application.useContainer(container)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    private fun beginLogin(): CallbackSdk.Pending {
        awaitText(loginText)
        val previous = sdk.pending
        clickText(loginText)
        await { sdk.pending != null && sdk.pending !== previous }
        return requireNotNull(sdk.pending)
    }

    private fun recreateActivity() {
        val current = requireNotNull(scenario)
        lateinit var old: MainActivity
        current.onActivity { old = it }
        current.recreate()
        current.onActivity {
            assertNotSame("ActivityScenario must create a new Activity", old, it)
            assertTrue("The old Activity must be destroyed", old.isDestroyed)
        }
        instrumentation.waitForIdleSync()
        awaitText("가상 근무표 시연 보기 (서버 미연동)")
    }

    private fun complete(pending: CallbackSdk.Pending, token: OAuthToken? = null, error: Throwable? = null) {
        instrumentation.runOnMainSync { pending.callback(token, error) }
    }

    private fun assertLoginCompleted(nonce: String) {
        awaitText("가상 회전 사용자님, 안녕하세요.")
        assertTrue(container.session.state.value is SessionState.SignedIn)
        assertEquals("fake-rotation-refresh", store.token)
        assertEquals(1, loginCalls.get())
        assertEquals(KakaoLoginBody("fake-sdk-id", nonce), loginBody.get())
    }

    private fun awaitText(text: String) {
        await { hasText(text) }
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        fail("Device state did not reach the expected value")
    }

    private fun findText(node: AccessibilityNodeInfo?, text: String, substring: Boolean = false): AccessibilityNodeInfo? {
        if (node == null) return null
        val value = node.text?.toString()
        if (node.isVisibleToUser && if (substring) value?.contains(text) == true else value == text) return node
        for (i in 0 until node.childCount) findText(node.getChild(i), text, substring)?.let { return it }
        return null
    }

    private fun hasText(text: String, substring: Boolean = false) =
        findText(instrumentation.uiAutomation.rootInActiveWindow, text, substring) != null

    private fun clickText(text: String) {
        var node = findText(instrumentation.uiAutomation.rootInActiveWindow, text)
        while (node != null && !node.isClickable) node = node.parent
        assertTrue("Expected enabled button: $text", node?.isEnabled == true)
        assertTrue("Expected clickable button: $text", node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun activity(context: Context): Activity = when (context) {
        is Activity -> context
        is ContextWrapper -> activity(context.baseContext)
        else -> error("Expected an Activity context")
    }
    private fun token() = OAuthToken("fake-sdk-access", Date(Long.MAX_VALUE), "fake-sdk-refresh",
        Date(Long.MAX_VALUE), "fake-sdk-id", emptyList())

    private class CallbackSdk : KakaoLoginSdk {
        data class Pending(val context: Context, val nonce: String, val callback: (OAuthToken?, Throwable?) -> Unit)
        override val isConfigured = true
        var talkAvailable = false
        var realAccountLogin = false
        @Volatile var talkCalls = 0
        @Volatile var accountCalls = 0
        @Volatile var pending: Pending? = null
        override fun isKakaoTalkLoginAvailable(context: Context) = talkAvailable
        override fun loginWithKakaoTalk(context: Context, nonce: String, callback: (OAuthToken?, Throwable?) -> Unit) {
            talkCalls++
            pending = Pending(context, nonce, callback)
        }
        override fun loginWithKakaoAccount(context: Context, nonce: String, callback: (OAuthToken?, Throwable?) -> Unit) {
            accountCalls++
            pending = Pending(context, nonce, callback)
            if (realAccountLogin) AndroidKakaoLoginSdk().loginWithKakaoAccount(context, nonce, callback)
        }
        override fun logout() = Unit
    }
}
