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
        var loginGate: CompletableDeferred<Unit>? = null
        var refreshGate: CompletableDeferred<Unit>? = null
        var logoutGate: CompletableDeferred<Unit>? = null
        val refreshStarted = CompletableDeferred<Unit>()
        val loginStarted = CompletableDeferred<Unit>()
        val logoutStarted = CompletableDeferred<Unit>()
        override suspend fun kakaoLogin(body: KakaoLoginBody): SessionDto {
            loginStarted.complete(Unit)
            loginGate?.await()
            return session("initial")
        }
        override suspend fun refresh(body: RefreshTokenBody): SessionDto {
            refreshCalls++
            refreshStarted.complete(Unit)
            refreshGate?.await()
            delay(20)
            failure?.let { throw it }
            return session("rotated")
        }
        override suspend fun logout(body: RefreshTokenBody) {
            logoutStarted.complete(Unit)
            logoutGate?.await()
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

    @Test fun logoutDoesNotWaitForRefreshAndDiscardsItsLateSuccess() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val api = FakeAuth().apply { refreshGate = gate }
        val store = MemoryTokenStore()
        val manager = SessionManager(api, store)
        manager.loginWithKakao("fake-id", "fake-nonce")
        val refresh = async { manager.refreshAfterUnauthorized(manager.accessToken) }
        try {
            withTimeout(1_000) { api.refreshStarted.await(); manager.logout() }
            assertFalse(refresh.isCompleted)
            assertSignedOut(manager, store)
            // A new process must not recover the credentials while the old request is in flight.
            val restarted = SessionManager(FakeAuth(), store)
            restarted.restore()
            assertEquals(SessionState.SignedOut, restarted.state.value)
        } finally { gate.complete(Unit) }
        assertNull(refresh.await())
        assertSignedOut(manager, store)
    }

    @Test fun logoutDuringRestoreDiscardsLateSuccessAndFailure() = runBlocking {
        for (failure in listOf(null, ApiException(0, "NETWORK", "offline"), ApiException(401, "EXPIRED", "expired"))) {
            val gate = CompletableDeferred<Unit>()
            val api = FakeAuth().apply { refreshGate = gate; this.failure = failure }
            val store = MemoryTokenStore("fake-refresh-saved")
            val manager = SessionManager(api, store)
            val restore = async { manager.restore() }
            try {
                withTimeout(1_000) { api.refreshStarted.await(); manager.logout() }
                assertSignedOut(manager, store)
            } finally { gate.complete(Unit) }
            restore.await()
            assertSignedOut(manager, store)
        }
    }

    @Test fun logoutDuringLoginDiscardsLateCredentials() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val api = FakeAuth().apply { loginGate = gate }
        val store = MemoryTokenStore()
        val manager = SessionManager(api, store)
        val login = async { manager.loginWithKakao("fake-id", "fake-nonce") }
        try {
            withTimeout(1_000) { api.loginStarted.await(); manager.logout() }
            assertSignedOut(manager, store)
        } finally { gate.complete(Unit) }
        login.await()
        assertSignedOut(manager, store)
    }

    @Test fun localLogoutCompletesBeforeServerRevocation() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val api = FakeAuth().apply { logoutGate = gate }
        val store = MemoryTokenStore()
        val manager = SessionManager(api, store)
        manager.loginWithKakao("fake-id", "fake-nonce")
        val logout = async { manager.logout() }
        try {
            withTimeout(1_000) { api.logoutStarted.await() }
            assertFalse(logout.isCompleted)
            assertSignedOut(manager, store)
            // Server logout does not hold the credentials lock or block a fresh login.
            withTimeout(1_000) { manager.loginWithKakao("fake-id", "fake-nonce") }
        } finally { gate.complete(Unit) }
        logout.await()
        assertTrue(manager.state.value is SessionState.SignedIn)
        assertEquals("fake-refresh-initial", store.token)
    }

    private fun assertSignedOut(manager: SessionManager, store: MemoryTokenStore) {
        assertEquals(SessionState.SignedOut, manager.state.value)
        assertNull(manager.accessToken)
        assertNull(store.token)
    }

    companion object {
        fun session(suffix: String) = SessionDto("fake-access-$suffix", "2099-01-01T00:00:00Z",
            "fake-refresh-$suffix", "2099-02-01T00:00:00Z", SessionUserDto("demo-user", "가상 사용자"))
    }
}
