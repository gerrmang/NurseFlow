package com.nurseflow.app.core.session

import com.nurseflow.app.core.network.ApiException
import com.nurseflow.app.core.network.AuthApi
import com.nurseflow.app.core.network.KakaoLoginBody
import com.nurseflow.app.core.network.RefreshTokenBody
import com.nurseflow.app.core.network.SessionDto
import com.nurseflow.app.core.network.apiCall
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SessionState {
    data object Restoring : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val userId: String, val displayName: String) : SessionState
}

/**
 * Owns the NurseFlow session: access token in memory, refresh token in [SecureTokenStore].
 * Refreshes are serialized with a mutex because the server revokes the whole session when a
 * rotated refresh token is presented twice (parallel 401s must not each refresh).
 */
class SessionManager(private val authApi: AuthApi, private val store: SecureTokenStore) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    @Volatile
    var accessToken: String? = null
        private set

    private val _pendingInvite = MutableStateFlow<String?>(null)

    /**
     * Invitation code from a tapped invite link or typed before signing in. The signed-in
     * navigation opens the join screen for it and then clears it.
     */
    val pendingInvite: StateFlow<String?> = _pendingInvite.asStateFlow()

    var pendingInviteCode: String?
        get() = _pendingInvite.value
        set(value) { _pendingInvite.value = value }

    suspend fun restore() {
        mutex.withLock {
            val refresh = store.read()
            if (refresh == null) {
                _state.value = SessionState.SignedOut
                return
            }
            try {
                apply(apiCall { authApi.refresh(RefreshTokenBody(refresh)) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                if (e.status == 401) store.clear()
                // Offline at start-up keeps the stored token for the next launch.
                accessToken = null
                _state.value = SessionState.SignedOut
            } catch (_: Exception) {
                // Unexpected response shape: never crash on start-up, just ask for login again.
                accessToken = null
                _state.value = SessionState.SignedOut
            }
        }
    }

    suspend fun loginWithKakao(idToken: String, nonce: String) {
        val session = apiCall { authApi.kakaoLogin(KakaoLoginBody(idToken, nonce)) }
        mutex.withLock { apply(session) }
    }

    /**
     * Called from the HTTP authenticator after a 401. If another request already refreshed
     * (the token differs from [usedToken]) the current token is returned without a new refresh.
     */
    suspend fun refreshAfterUnauthorized(usedToken: String?): String? = mutex.withLock {
        val current = accessToken
        if (current != null && current != usedToken) return@withLock current
        val refresh = store.read()
        if (refresh == null) {
            signOutLocked()
            return@withLock null
        }
        try {
            apply(apiCall { authApi.refresh(RefreshTokenBody(refresh)) })
            accessToken
        } catch (e: ApiException) {
            if (e.status == 401) signOutLocked()
            null
        }
    }

    suspend fun logout() {
        mutex.withLock {
            store.read()?.let { refresh -> runCatching { apiCall { authApi.logout(RefreshTokenBody(refresh)) } } }
            signOutLocked()
        }
    }

    private suspend fun apply(session: SessionDto) {
        val refresh = requireNotNull(session.refreshToken) { "refresh token missing in body mode" }
        store.write(refresh)
        accessToken = session.accessToken
        _state.value = SessionState.SignedIn(session.user.userId, session.user.displayName)
    }

    private suspend fun signOutLocked() {
        store.clear()
        accessToken = null
        _state.value = SessionState.SignedOut
    }
}
