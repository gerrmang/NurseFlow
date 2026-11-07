package com.nurseflow.app.feature.auth

import android.content.Context
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.user.UserApiClient
import com.nurseflow.app.BuildConfig

/** SDK boundary; callback adaptation and cancellation remain in KakaoLoginClient. */
interface KakaoLoginSdk {
    val isConfigured: Boolean
    fun isKakaoTalkLoginAvailable(context: Context): Boolean
    fun loginWithKakaoTalk(context: Context, nonce: String, callback: (OAuthToken?, Throwable?) -> Unit)
    fun loginWithKakaoAccount(context: Context, nonce: String, callback: (OAuthToken?, Throwable?) -> Unit)
    fun logout()
}

internal class AndroidKakaoLoginSdk : KakaoLoginSdk {
    override val isConfigured: Boolean get() = BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()
    override fun isKakaoTalkLoginAvailable(context: Context) = UserApiClient.instance.isKakaoTalkLoginAvailable(context)
    override fun loginWithKakaoTalk(context: Context, nonce: String, callback: (OAuthToken?, Throwable?) -> Unit) {
        UserApiClient.instance.loginWithKakaoTalk(context, nonce = nonce, callback = callback)
    }
    override fun loginWithKakaoAccount(context: Context, nonce: String, callback: (OAuthToken?, Throwable?) -> Unit) {
        UserApiClient.instance.loginWithKakaoAccount(context, nonce = nonce, callback = callback)
    }
    override fun logout() { if (isConfigured) UserApiClient.instance.logout { } }
}
