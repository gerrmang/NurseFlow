package com.nurseflow.app
import android.content.Context
import com.nurseflow.app.core.network.ApiFactory
import com.nurseflow.app.core.session.SecureTokenStore
import com.nurseflow.app.core.session.SessionManager
import com.nurseflow.app.core.session.TokenStore
import com.nurseflow.app.feature.auth.KakaoLoginClient
import kotlinx.coroutines.*

class AppContainer(
    context: Context,
    apiBaseUrl: String = BuildConfig.API_BASE_URL,
    tokenStore: TokenStore = SecureTokenStore(context),
) {
    val session = SessionManager(ApiFactory.authApi(apiBaseUrl), tokenStore)
    val api = ApiFactory.nurseFlowApi(apiBaseUrl, session)
    val kakao = KakaoLoginClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    init { restoreSession() }
    // Startup and UI retries share the application lifetime, including Activity recreation.
    fun restoreSession() = scope.launch { session.restore() }
    fun logout() { scope.launch { try { session.logout() } finally { kakao.logout() } } }
}
