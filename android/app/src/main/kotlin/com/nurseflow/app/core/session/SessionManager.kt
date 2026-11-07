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
    data object LogoutFailed : SessionState
    data class RestoreFailed(val message: String) : SessionState
    data class SignedIn(val userId: String, val displayName: String, val generation: Long) : SessionState
}

/** Network operations are serialized separately from local credentials. Tokens never enter UI state. */
class SessionManager(private val authApi: AuthApi, private val store: TokenStore) {
    private val mutex = Mutex()
    private val requests = Mutex()
    private val mutableState = MutableStateFlow<SessionState>(SessionState.Restoring)
    val state = mutableState.asStateFlow()
    private var generation = 0L
    private var revision = 0L
    @Volatile var accessToken: String? = null
        private set

    suspend fun restore() {
        val started = mutex.withLock {
            // Never reuse credentials whose deletion failed in this process.
            if (mutableState.value == SessionState.LogoutFailed) {
                signOutLocked()
                return
            }
            revision
        }
        requests.withLock {
            try {
                val refresh = mutex.withLock {
                    if (started != revision) return
                    mutableState.value = SessionState.Restoring
                    store.read().also { if (it == null) signOutLocked() }
                } ?: return
                val result = apiCall { authApi.refresh(RefreshTokenBody(refresh)) }
                mutex.withLock { if (started == revision) apply(result) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutex.withLock {
                    if (started != revision) return
                    accessToken = null
                    if (e is ApiException && e.status == 401) signOutLocked()
                    else mutableState.value = SessionState.RestoreFailed(e.userMessage())
                }
            }
        }
    }

    suspend fun loginWithKakao(idToken: String, nonce: String) {
        val started = mutex.withLock { revision }
        requests.withLock {
            mutex.withLock { if (started != revision) return }
            val result = apiCall { authApi.kakaoLogin(KakaoLoginBody(idToken, nonce)) }
            mutex.withLock { if (started == revision) apply(result) }
        }
    }

    suspend fun refreshAfterUnauthorized(usedToken: String?): String? {
        val started = mutex.withLock { revision }
        return requests.withLock {
            try {
                val refresh = mutex.withLock {
                    // Queued requests and late responses cannot restore a logged-out session.
                    if (started != revision || mutableState.value !is SessionState.SignedIn || usedToken == null) return null
                    val current = accessToken
                    if (current != null && current != usedToken) return current
                    store.read().also { if (it == null) signOutLocked() }
                } ?: return null
                val result = apiCall { authApi.refresh(RefreshTokenBody(refresh)) }
                mutex.withLock {
                    if (started != revision) return null
                    apply(result)
                    accessToken
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                mutex.withLock { if (started == revision && e.status == 401) signOutLocked() }
                null
            }
        }
    }

    suspend fun logout() {
        val refresh = mutex.withLock {
            try { store.read() }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null } // Unreadable credentials cannot be sent for revocation.
            finally { signOutLocked() }
        }
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
        revision++
        accessToken = null
        mutableState.value = SessionState.SignedOut
        try { store.clear() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableState.value = SessionState.LogoutFailed }
    }
}
