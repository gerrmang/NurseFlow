package com.nurseflow.app.feature.auth

import android.content.Context
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import com.nurseflow.app.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.SecureRandom
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class KakaoLoginCancelled : Exception("카카오 로그인을 취소했어요.")
class KakaoLoginFailed(message: String) : Exception(message)

/**
 * Wraps the Kakao SDK: KakaoTalk app login when available, otherwise Kakao account login in
 * a browser tab. Requests an OpenID Connect ID token bound to a fresh nonce, which the server
 * verifies. Kakao access/refresh tokens are not sent to NurseFlow.
 */
class KakaoLoginClient {
    val isConfigured: Boolean get() = BuildConfig.KAKAO_NATIVE_APP_KEY.isNotBlank()

    fun newNonce(): String {
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Returns the Kakao ID token. [context] must be an Activity context. */
    suspend fun login(context: Context, nonce: String): String {
        if (!isConfigured) throw KakaoLoginFailed("카카오 앱 키가 설정되지 않았어요. local.properties의 kakao.nativeAppKey를 확인해 주세요.")
        val client = UserApiClient.instance
        val token = if (client.isKakaoTalkLoginAvailable(context)) {
            try {
                talkLogin(context, nonce)
            } catch (e: KakaoLoginCancelled) {
                throw e
            } catch (_: Exception) {
                // KakaoTalk installed but not logged in, etc.: fall back to account login.
                accountLogin(context, nonce)
            }
        } else {
            accountLogin(context, nonce)
        }
        return token.idToken
            ?: throw KakaoLoginFailed("ID 토큰을 받지 못했어요. Kakao Developers에서 OpenID Connect를 켜 주세요.")
    }

    /** Clears the Kakao SDK token on this device. NurseFlow's own logout is separate. */
    fun logout() {
        if (isConfigured) UserApiClient.instance.logout { }
    }

    private suspend fun talkLogin(context: Context, nonce: String): OAuthToken = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.loginWithKakaoTalk(context, nonce = nonce) { token, error -> cont.complete(token, error) }
    }

    private suspend fun accountLogin(context: Context, nonce: String): OAuthToken = suspendCancellableCoroutine { cont ->
        UserApiClient.instance.loginWithKakaoAccount(context, nonce = nonce) { token, error -> cont.complete(token, error) }
    }

    private fun kotlinx.coroutines.CancellableContinuation<OAuthToken>.complete(token: OAuthToken?, error: Throwable?) {
        if (!isActive) return
        when {
            error is ClientError && error.reason == ClientErrorCause.Cancelled -> resumeWithException(KakaoLoginCancelled())
            error != null -> resumeWithException(KakaoLoginFailed("카카오 로그인에 실패했어요. 잠시 후 다시 시도해 주세요."))
            token != null -> resume(token)
            else -> resumeWithException(KakaoLoginFailed("카카오 로그인 결과가 없어요."))
        }
    }
}
