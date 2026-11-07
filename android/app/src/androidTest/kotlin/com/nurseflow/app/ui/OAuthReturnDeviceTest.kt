package com.nurseflow.app.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kakao.sdk.auth.AuthCodeHandlerActivity
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.AuthError
import com.nurseflow.app.AppContainer
import com.nurseflow.app.BuildConfig
import com.nurseflow.app.MainActivity
import com.nurseflow.app.core.session.SessionState
import com.nurseflow.app.feature.auth.AndroidKakaoLoginSdk
import com.nurseflow.app.feature.auth.KakaoLoginClient
import com.nurseflow.app.feature.auth.KakaoLoginSdk
import com.nurseflow.app.test.IsolatedSessionTestApplication
import com.nurseflow.app.test.TestTokenStore
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/** Opt-in, separate process: real SDK + manifest/OS deep-link routing; external consent is simulated. */
@RunWith(AndroidJUnit4::class)
class OAuthReturnDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun deniedOAuthDeepLinkReturnsToRecreatedActivity() {
        assumeTrue("Run this class alone with -e oauthReturnProbe true",
            InstrumentationRegistry.getArguments().getString("oauthReturnProbe") == "true" &&
                InstrumentationRegistry.getArguments().getString("class") == OAuthReturnDeviceTest::class.java.name)
        assumeTrue("Build with a configured Kakao native key; never print its value",
            BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank())
        val server = MockWebServer()
        server.start()
        val baseUrl = server.url("/").toString()
        val sdk = TalkFailureThenRealAccountSdk()
        val store = TestTokenStore()
        val context = instrumentation.targetContext
        lateinit var container: AppContainer
        var scenario: ActivityScenario<MainActivity>? = null
        var sdkActivity: Activity? = null
        val delivered = AtomicInteger()
        val dispatchFailures = AtomicInteger()
        val callback = Intent(Intent.ACTION_VIEW, Uri.Builder()
            .scheme("kakao${BuildConfig.KAKAO_NATIVE_APP_KEY}").authority("oauth")
            .appendQueryParameter("error", "access_denied")
            .appendQueryParameter("error_description", "fake consent denial").build())
            .addCategory(Intent.CATEGORY_BROWSABLE).addCategory(Intent.CATEGORY_DEFAULT)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).setPackage(context.packageName)
        val browser = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != Intent.ACTION_CHOOSER &&
                    !(intent.action == Intent.ACTION_VIEW && intent.data?.scheme in listOf("https", "http"))) return null
                // Block the external browser, then return through the real manifest/SDK handler.
                Handler(Looper.getMainLooper()).post {
                    try {
                        context.startActivity(callback)
                        delivered.incrementAndGet()
                    } catch (_: Exception) { dispatchFailures.incrementAndGet() }
                }
                return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
            }
        }
        instrumentation.addMonitor(browser)
        val account = instrumentation.addMonitor(AuthCodeHandlerActivity::class.java.name, null, false)
        try {
            assertEquals(AuthCodeHandlerActivity::class.java.name,
                callback.resolveActivity(context.packageManager)?.className)
            instrumentation.runOnMainSync {
                container = AppContainer(context, baseUrl, store, KakaoLoginClient(sdk))
                (context.applicationContext as IsolatedSessionTestApplication).useContainer(container)
            }
            scenario = ActivityScenario.launch(MainActivity::class.java)
            await { hasText("카카오 로그인") }
            clickText("카카오 로그인")
            await { sdk.talkCallback != null }
            lateinit var original: MainActivity
            scenario.onActivity { original = it }
            scenario.recreate()
            scenario.onActivity { assertNotSame(original, it); assertTrue(original.isDestroyed) }
            instrumentation.runOnMainSync {
                requireNotNull(sdk.talkCallback)(null, IOException("fake Talk fallback"))
            }
            sdkActivity = instrumentation.waitForMonitorWithTimeout(account, 10_000)
            assertNotNull("The SDK account Activity must start", sdkActivity)
            await { delivered.get() == 1 || dispatchFailures.get() > 0 }
            assertEquals(0, dispatchFailures.get())
            await { sdk.accountCallbacks.get() == 1 && hasText("카카오 로그인") }
            assertTrue("The actual SDK must parse the returned OAuth denial", sdk.returnedError is AuthError)
            assertEquals("fake consent denial", (sdk.returnedError as AuthError).response.errorDescription)
            assertEquals(SessionState.SignedOut, container.session.state.value)
            assertNull(container.session.accessToken)
            assertNull(store.token)
            assertEquals(0, server.requestCount)
        } finally {
            sdkActivity?.let { activity -> instrumentation.runOnMainSync { activity.finish() } }
            scenario?.close()
            instrumentation.removeMonitor(account)
            instrumentation.removeMonitor(browser)
            server.shutdown()
        }
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (predicate()) return
            SystemClock.sleep(50)
        }
        fail("OAuth return did not reach the expected state")
    }

    private fun findText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isVisibleToUser && node.text?.toString() == text) return node
        for (i in 0 until node.childCount) findText(node.getChild(i), text)?.let { return it }
        return null
    }
    private fun hasText(text: String) = findText(instrumentation.uiAutomation.rootInActiveWindow, text) != null
    private fun clickText(text: String) {
        var node = findText(instrumentation.uiAutomation.rootInActiveWindow, text)
        while (node != null && !node.isClickable) node = node.parent
        assertTrue(node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
    }

    private class TalkFailureThenRealAccountSdk : KakaoLoginSdk by AndroidKakaoLoginSdk() {
        @Volatile var talkCallback: ((OAuthToken?, Throwable?) -> Unit)? = null
        val accountCallbacks = AtomicInteger()
        @Volatile var returnedError: Throwable? = null
        override fun isKakaoTalkLoginAvailable(context: Context) = true
        override fun loginWithKakaoTalk(context: Context, nonce: String, callback: (OAuthToken?, Throwable?) -> Unit) {
            talkCallback = callback
        }
        override fun loginWithKakaoAccount(context: Context, nonce: String, callback: (OAuthToken?, Throwable?) -> Unit) {
            AndroidKakaoLoginSdk().loginWithKakaoAccount(context, nonce) { token, error ->
                returnedError = error
                accountCallbacks.incrementAndGet()
                callback(token, error)
            }
        }
    }
}
