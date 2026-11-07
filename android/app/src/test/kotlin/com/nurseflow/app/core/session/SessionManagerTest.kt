package com.nurseflow.app.core.session

import com.nurseflow.app.core.network.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class MemoryTokenStore(var token: String? = null) : TokenStore {
    override suspend fun read() = token
    override suspend fun write(refreshToken: String) { token = refreshToken }
    override suspend fun clear() { token = null }
}

class SessionManagerTest {
    private class FakeAuth : AuthApi {
        var refreshCalls = 0
        var failure: Exception? = null
        var logoutFailure = false
        override suspend fun kakaoLogin(body: KakaoLoginBody) = session("initial")
        override suspend fun refresh(body: RefreshTokenBody): SessionDto {
            refreshCalls++
            delay(20)
            failure?.let { throw it }
            return session("rotated")
        }
        override suspend fun logout(body: RefreshTokenBody) {
            if (logoutFailure) throw ApiException(0, "NETWORK", "offline")
        }
    }

    @Test fun concurrentUnauthorizedRequestsRotateOnlyOnce() = runBlocking {
        val api = FakeAuth()
        val store = MemoryTokenStore()
        val manager = SessionManager(api, store)
        manager.loginWithKakao("fake-id", "fake-nonce")
        val results = (1..12).map { async { manager.refreshAfterUnauthorized("fake-access-initial") } }.awaitAll()
        assertEquals(1, api.refreshCalls)
        assertTrue(results.all { it == "fake-access-rotated" })
        assertEquals("fake-refresh-rotated", store.token)
    }

    @Test fun expiredOrReusedRefreshSignsOutAndClearsToken() = runBlocking {
        val api = FakeAuth()
        val store = MemoryTokenStore()
        val manager = SessionManager(api, store)
        manager.loginWithKakao("fake-id", "fake-nonce")
        api.failure = ApiException(401, "REFRESH_TOKEN_REUSED", "expired")
        assertNull(manager.refreshAfterUnauthorized(manager.accessToken))
        assertEquals(SessionState.SignedOut, manager.state.value)
        assertNull(store.token)
        assertNull(manager.accessToken)
    }

    @Test fun offlineRestoreKeepsTokenAndCanBeRetried() = runBlocking {
        val api = FakeAuth().apply { failure = ApiException(0, "NETWORK", "offline") }
        val store = MemoryTokenStore("fake-refresh-saved")
        val manager = SessionManager(api, store)
        manager.restore()
        assertTrue(manager.state.value is SessionState.RestoreFailed)
        assertEquals("fake-refresh-saved", store.token)
        api.failure = null
        manager.restore()
        assertTrue(manager.state.value is SessionState.SignedIn)
    }

    @Test fun expiredStartupTokenIsRemoved() = runBlocking {
        val api = FakeAuth().apply { failure = ApiException(401, "REFRESH_TOKEN_EXPIRED", "expired") }
        val store = MemoryTokenStore("fake-refresh-saved")
        val manager = SessionManager(api, store)
        manager.restore()
        assertEquals(SessionState.SignedOut, manager.state.value)
        assertNull(store.token)
    }

    @Test fun offlineLogoutClearsSessionAndLateUnauthorizedCannotRestoreIt() = runBlocking {
        val api = FakeAuth().apply { logoutFailure = true }
        val store = MemoryTokenStore()
        val manager = SessionManager(api, store)
        manager.loginWithKakao("fake-id", "fake-nonce")
        val previous = manager.accessToken
        manager.logout()
        assertNull(manager.refreshAfterUnauthorized(previous))
        assertEquals(0, api.refreshCalls)
        assertEquals(SessionState.SignedOut, manager.state.value)
        assertNull(manager.accessToken)
        assertNull(store.token)
    }

    @Test fun rotatedSessionKeepsUiIdentityButReloginChangesIt() = runBlocking {
        val manager = SessionManager(FakeAuth(), MemoryTokenStore())
        manager.loginWithKakao("fake-id", "fake-nonce")
        val before = manager.state.value
        manager.refreshAfterUnauthorized(manager.accessToken)
        assertEquals(before, manager.state.value)
        manager.logout()
        manager.loginWithKakao("fake-id", "fake-nonce")
        assertNotEquals(before, manager.state.value)
    }

    companion object {
        fun session(suffix: String) = SessionDto("fake-access-$suffix", "2099-01-01T00:00:00Z",
            "fake-refresh-$suffix", "2099-02-01T00:00:00Z", SessionUserDto("demo-user", "가상 사용자"))
    }
}
