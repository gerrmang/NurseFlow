package com.nurseflow.app.core.session

import com.nurseflow.app.core.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SessionState {
    data object Restoring : SessionState
    data object SignedOut : SessionState
    data class RestoreFailed(val message: String) : SessionState
    data class SignedIn(val userId: String, val displayName: String, val generation: Long) : SessionState
}

/** All token rotation and local storage changes share one lock. Tokens never enter UI state. */
class SessionManager(private val authApi: AuthApi, private val store: TokenStore) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    @Volatile var accessToken: String? = null
        private set

    suspend fun restore() = mutex.withLock {
        mutableState.value = SessionState.Restoring
        try {
            val refresh = store.read()
            if (refresh == null) signOutLocked()
            else apply(apiCall { authApi.refresh(RefreshTokenBody(refresh)) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            accessToken = null
            if (e is ApiException && e.status == 401) signOutLocked()
            else mutableState.value = SessionState.RestoreFailed(e.userMessage())
        }
    }

    suspend fun loginWithKakao(idToken: String, nonce: String) = mutex.withLock {
        apply(apiCall { authApi.kakaoLogin(KakaoLoginBody(idToken, nonce)) })
    }

    suspend fun refreshAfterUnauthorized(usedToken: String?): String? = mutex.withLock {
        // A response from a logged-out session may not restore credentials.
        if (mutableState.value !is SessionState.SignedIn || usedToken == null) return@withLock null
        val current = accessToken
        if (current != null && current != usedToken) return@withLock current
        try {
            val refresh = store.read()
            if (refresh == null) {
                signOutLocked()
                return@withLock null
            }
            apply(apiCall { authApi.refresh(RefreshTokenBody(refresh)) })
            accessToken
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            if (e.status == 401) signOutLocked()
            null
        }
    }

    suspend fun logout() = mutex.withLock {
        val refresh = try { store.read() } finally { signOutLocked() }
        if (refresh != null) {
            try { apiCall { authApi.logout(RefreshTokenBody(refresh)) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Local logout still succeeds when offline. */ }
        }
    }

    private suspend fun apply(session: SessionDto) = withContext(NonCancellable) {
        val refresh = requireNotNull(session.refreshToken)
        try { store.write(refresh) }
        catch (e: Exception) { signOutLocked(); throw e }
        accessToken = session.accessToken
        val previous = mutableState.value as? SessionState.SignedIn
        if (previous == null || previous.userId != session.user.userId) generation++
        mutableState.value = SessionState.SignedIn(session.user.userId, session.user.displayName, generation)
    }

    private suspend fun signOutLocked() = withContext(NonCancellable) {
        accessToken = null
        mutableState.value = SessionState.SignedOut
        store.clear()
    }
}
