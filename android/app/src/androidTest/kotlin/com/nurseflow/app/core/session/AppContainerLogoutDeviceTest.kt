package com.nurseflow.app.core.session

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.kakao.sdk.auth.model.OAuthToken
import com.nurseflow.app.AppContainer
import com.nurseflow.app.core.network.KakaoLoginBody
import com.nurseflow.app.feature.auth.KakaoLoginClient
import com.nurseflow.app.feature.auth.KakaoLoginSdk
import com.nurseflow.app.test.TestTokenStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Date
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real AppContainer/SDK callback bridge/HTTP; gates the old session's server logout. */
@RunWith(AndroidJUnit4::class)
class AppContainerLogoutDeviceTest {
    @Test fun reloginSurvivesDelayedServerLogoutSuccess() = runBlocking { verifyRelogin(204) }
    @Test fun reloginSurvivesDelayedServerLogoutFailure() = runBlocking { verifyRelogin(500) }

    private suspend fun verifyRelogin(status: Int) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val server = server(entered, release, status)
        val sdk = DeviceSdk()
        val store = TestTokenStore()
        var logout: Job? = null
        try {
            val container = container(server, sdk, store)
            login(container, "initial")
            logout = withContext(Dispatchers.Main) { container.logout() }
            assertTrue("Old server logout must be in flight", entered.await(5, TimeUnit.SECONDS))
            assertFalse(logout.isCompleted)
            assertEquals(SessionState.SignedOut, container.session.state.value)
            assertNull(store.token)
            assertNull("SDK cache must be cleared before waiting for the server", sdk.token)
            assertEquals(1, sdk.clears)
            withTimeout(5_000) { login(container, "new") }
            assertEquals("fake-sdk-new", sdk.token)
            assertEquals("fake-refresh-new", store.token)
            val fresh = container.session.state.value
            release.countDown()
            withTimeout(5_000) { logout.join() }
            assertFalse(logout.isCancelled)
            assertEquals("Old logout must not clear a new SDK login", "fake-sdk-new", sdk.token)
            assertEquals(1, sdk.clears)
            assertEquals("fake-refresh-new", store.token)
            assertEquals("fake-access-new", container.session.accessToken)
            assertEquals(fresh, container.session.state.value)
        } finally {
            release.countDown()
            withContext(NonCancellable) { withTimeout(5_000) { logout?.join() } }
            server.shutdown()
        }
    }

    @Test fun sdkCacheFailureDoesNotCancelNurseFlowLogout() = runBlocking {
        val entered = CountDownLatch(1)
        val server = server(entered, CountDownLatch(0), 204)
        val sdk = DeviceSdk()
        val store = TestTokenStore()
        try {
            val container = container(server, sdk, store)
            login(container, "initial")
            sdk.clearFailure = true
            val logout = withContext(Dispatchers.Main) { container.logout() }
            withTimeout(5_000) { logout.join() }
            assertFalse(logout.isCancelled)
            assertEquals(0L, entered.count)
            assertEquals(1, sdk.clears)
            assertEquals(SessionState.SignedOut, container.session.state.value)
            assertNull(container.session.accessToken)
            assertNull(store.token)
        } finally { server.shutdown() }
    }

    private suspend fun container(server: MockWebServer, sdk: DeviceSdk, store: TestTokenStore): AppContainer {
        val baseUrl = server.url("/").toString() // Resolve outside the Android main thread.
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val container = withContext(Dispatchers.Main) { AppContainer(context, baseUrl, store, KakaoLoginClient(sdk)) }
        withTimeout(5_000) { container.session.state.first { it == SessionState.SignedOut } }
        return container
    }

    private suspend fun login(container: AppContainer, suffix: String) = withContext(Dispatchers.Main) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val idToken = container.kakao.login(context, suffix)
        container.session.loginWithKakao(idToken, suffix)
    }

    private fun server(entered: CountDownLatch, release: CountDownLatch, status: Int) = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/v1/auth/logout" -> {
                    entered.countDown()
                    check(release.await(15, TimeUnit.SECONDS)) { "Test logout gate timed out" }
                    MockResponse().setResponseCode(status)
                }
                "/api/v1/auth/kakao/session" -> {
                    val body = Json.decodeFromString<KakaoLoginBody>(request.body.readUtf8())
                    val suffix = if (body.idToken == "fake-id-new") "new" else "initial"
                    MockResponse().setHeader("Content-Type", "application/json").setBody(
                        """{"accessToken":"fake-access-$suffix","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fake-refresh-$suffix","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"fake-user-$suffix","displayName":"가상 사용자"}}""",
                    )
                }
                else -> MockResponse().setResponseCode(404)
            }
        }
        start()
    }

    private class DeviceSdk : KakaoLoginSdk {
        override val isConfigured = true
        @Volatile var token: String? = null
        @Volatile var clears = 0
        var clearFailure = false
        override fun isKakaoTalkLoginAvailable(context: Context) = false
        override fun loginWithKakaoTalk(context: Context, nonce: String, callback: (OAuthToken?, Throwable?) -> Unit) =
            error("Account login expected")
        override fun loginWithKakaoAccount(context: Context, nonce: String, callback: (OAuthToken?, Throwable?) -> Unit) {
            token = "fake-sdk-$nonce"
            callback(OAuthToken("fake-sdk-$nonce", Date(Long.MAX_VALUE), "fake-sdk-refresh-$nonce",
                Date(Long.MAX_VALUE), "fake-id-$nonce", emptyList()), null)
        }
        override fun logout() {
            clears++
            if (clearFailure) throw IOException("fake SDK cache failure")
            token = null
        }
    }
}
