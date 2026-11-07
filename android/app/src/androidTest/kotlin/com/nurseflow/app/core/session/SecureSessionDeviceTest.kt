package com.nurseflow.app.core.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Real device Keystore/DataStore, fake identity provider; never calls Kakao or the backend. */
@RunWith(AndroidJUnit4::class)
class SecureSessionDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private suspend fun withEmptyStore(test: suspend (SecureTokenStore) -> Unit) {
        val store = SecureTokenStore(context)
        // Do not replace a developer's existing session on a reused emulator.
        assumeTrue("Use a signed-out test device", store.read() == null)
        try { test(store) } finally { store.clear() }
    }

    @Test fun ciphertextPersistsAndAnotherStoreCanDecryptIt() = runBlocking {
        withEmptyStore { store ->
            val fake = "fake-device-refresh-not-a-credential"
            store.write(fake)
            assertEquals(fake, SecureTokenStore(context).read())
            val persisted = File(context.filesDir, "datastore/session.preferences_pb").readBytes()
            assertTrue(persisted.isNotEmpty())
            assertFalse(String(persisted, Charsets.ISO_8859_1).contains(fake))
            store.clear()
            assertNull(SecureTokenStore(context).read())
        }
    }

    @Test fun newManagerRestoresRotatesAndLogsOutUsingDeviceStorage() = runBlocking {
        withEmptyStore { store ->
            val api = FakeAuth()
            SessionManager(api, store).loginWithKakao("fake-id", "fake-nonce")
            val restored = SessionManager(api, SecureTokenStore(context))
            restored.restore()
            assertTrue(restored.state.value is SessionState.SignedIn)
            assertEquals("fake-refresh-initial", api.lastRefresh)
            assertEquals("fake-refresh-rotated", store.read())
            restored.logout()
            assertEquals("fake-refresh-rotated", api.lastLogout)
            assertNull(store.read())
            assertNull(restored.accessToken)
            assertEquals(SessionState.SignedOut, restored.state.value)
        }
    }

    @Test fun rejectedRefreshClearsDeviceCredentials() = runBlocking {
        withEmptyStore { store ->
            store.write("fake-expired-refresh")
            val manager = SessionManager(FakeAuth(reject = true), store)
            manager.restore()
            assertNull(store.read())
            assertNull(manager.accessToken)
            assertEquals(SessionState.SignedOut, manager.state.value)
        }
    }

    private class FakeAuth(private val reject: Boolean = false) : AuthApi {
        var lastRefresh: String? = null
        var lastLogout: String? = null
        override suspend fun kakaoLogin(body: KakaoLoginBody) = result("initial")
        override suspend fun refresh(body: RefreshTokenBody): SessionDto {
            lastRefresh = body.refreshToken
            if (reject) throw ApiException(401, "INVALID_REFRESH_TOKEN", "expired")
            return result("rotated")
        }
        override suspend fun logout(body: RefreshTokenBody) { lastLogout = body.refreshToken }
        private fun result(suffix: String) = SessionDto(
            "fake-access-$suffix", "2099-01-01T00:00:00Z", "fake-refresh-$suffix",
            "2099-02-01T00:00:00Z", SessionUserDto("demo-user", "가상 사용자"),
        )
    }
}
