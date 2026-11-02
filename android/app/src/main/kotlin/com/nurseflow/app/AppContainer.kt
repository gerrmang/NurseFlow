package com.nurseflow.app

import android.content.Context
import com.nurseflow.app.core.network.ApiFactory
import com.nurseflow.app.core.session.SecureTokenStore
import com.nurseflow.app.core.session.SessionManager
import com.nurseflow.app.feature.auth.KakaoLoginClient
import com.nurseflow.app.feature.invite.InvitationRepository
import com.nurseflow.app.feature.profile.ProfileRepository
import com.nurseflow.app.feature.ward.WardRepository

/**
 * Manual dependency container (one instance per process). Kept small on purpose;
 * it can move to Hilt without changing the repositories or screens.
 */
class AppContainer(context: Context) {
    private val tokenStore = SecureTokenStore(context.applicationContext)
    private val authApi = ApiFactory.authApi(BuildConfig.API_BASE_URL)

    val session = SessionManager(authApi, tokenStore)
    private val api = ApiFactory.nurseFlowApi(BuildConfig.API_BASE_URL, session)

    val kakaoLogin = KakaoLoginClient()
    val wards = WardRepository(api)
    val invitations = InvitationRepository(api)
    val profiles = ProfileRepository(api)
}
