package com.nurseflow.app
import android.content.Context
import com.nurseflow.app.core.network.ApiFactory
import com.nurseflow.app.core.session.SecureTokenStore
import com.nurseflow.app.core.session.SessionManager
import com.nurseflow.app.core.session.SessionState
import com.nurseflow.app.core.session.TokenStore
import com.nurseflow.app.feature.auth.KakaoLoginClient
import com.nurseflow.app.feature.invite.PendingInvitationStore
import kotlinx.coroutines.*

class AppContainer(
    context: Context,
    apiBaseUrl: String = BuildConfig.API_BASE_URL,
    tokenStore: TokenStore = SecureTokenStore(context),
    val kakao: KakaoLoginClient = KakaoLoginClient(),
) {
    val session = SessionManager(ApiFactory.authApi(apiBaseUrl), tokenStore)
    val api = ApiFactory.nurseFlowApi(apiBaseUrl, session)
    val invitationApi = ApiFactory.invitationApi(apiBaseUrl, session)
    /** Invitation link being handled; memory only, never persisted. */
    val invites = PendingInvitationStore()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    init {
        restoreSession()
        // An invitation received during one account's session never carries over to another
        // account (logout, expired session or account switch). One received while signed out
        // is kept so the flow can resume after login.
        scope.launch {
            var previous: SessionState.SignedIn? = null
            session.state.collect { state ->
                val signedIn = state as? SessionState.SignedIn
                if (previous != null && (signedIn?.userId != previous?.userId || signedIn?.generation != previous?.generation)) {
                    invites.clear()
                }
                previous = signedIn
            }
        }
    }
    // Startup and UI retries share the application lifetime, including Activity recreation.
    fun restoreSession() = scope.launch { session.restore() }
    fun logout() = scope.launch {
        invites.clear()
        // SDK tokens belong to the session being ended. Never clear them after waiting for
        // server revocation: another login may already have completed by then.
        try { kakao.logout() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* NurseFlow logout still runs if the SDK cache is unavailable. */ }
        finally { session.logout() }
    }
}
