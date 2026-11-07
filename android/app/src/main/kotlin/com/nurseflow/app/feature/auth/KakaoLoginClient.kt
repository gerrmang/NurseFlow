package com.nurseflow.app.feature.auth

import android.content.Context
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.common.model.AuthError
import com.kakao.sdk.common.model.ApiError
import com.nurseflow.app.BuildConfig
import com.kakao.sdk.common.util.Utility
import kotlinx.coroutines.CancellationException
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
class KakaoLoginClient(private val sdk: KakaoLoginSdk = AndroidKakaoLoginSdk()) {
    val isConfigured: Boolean get() = sdk.isConfigured

    fun newNonce(): String {
        val bytes = ByteArray(24).also(SecureRandom()::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** Returns the Kakao ID token. [context] must be an Activity context. */
    suspend fun login(context: Context, nonce: String): String {
        if (!isConfigured) throw KakaoLoginFailed(
            if (BuildConfig.DEBUG) "카카오 앱 키가 설정되지 않았어요. local.properties의 kakao.nativeAppKey를 확인해 주세요."
            else "로그인 설정을 확인 중이에요. 관리자에게 문의해 주세요.",
        )
        val token = if (sdk.isKakaoTalkLoginAvailable(context)) {
            try {
                talkLogin(context, nonce)
            } catch (e: KakaoLoginCancelled) {
                throw e
            } catch (e: CancellationException) {
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
        sdk.logout()
    }

    private suspend fun talkLogin(context: Context, nonce: String): OAuthToken = suspendCancellableCoroutine { cont ->
        sdk.loginWithKakaoTalk(context, nonce) { token, error -> cont.complete(token, error, context) }
    }

    private suspend fun accountLogin(context: Context, nonce: String): OAuthToken = suspendCancellableCoroutine { cont ->
        sdk.loginWithKakaoAccount(context, nonce) { token, error -> cont.complete(token, error, context) }
    }

    private fun kotlinx.coroutines.CancellableContinuation<OAuthToken>.complete(token: OAuthToken?, error: Throwable?, context: Context) {
        if (!isActive) return
        when {
            error is ClientError && error.reason == ClientErrorCause.Cancelled -> resumeWithException(KakaoLoginCancelled())
            error != null -> {
                val debugHint = if (BuildConfig.DEBUG) {
                    "\n진단: ${safeKakaoDiagnostic(error)}" +
                        "\nKakao Developers의 Android 패키지·키 해시와 OIDC 설정을 확인하세요. 키 해시: " +
                        runCatching { Utility.getKeyHash(context) }.getOrDefault("확인 불가")
                } else ""
                resumeWithException(KakaoLoginFailed("카카오 로그인에 실패했어요. 잠시 후 다시 시도해 주세요." + debugHint))
            }
            token != null -> resume(token)
            else -> resumeWithException(KakaoLoginFailed("카카오 로그인 결과가 없어요."))
        }
    }
}

/** Only typed SDK codes and fixed labels; never expose descriptions, URLs or token values. */
internal fun safeKakaoDiagnostic(error: Throwable): String = when (error) {
    is AuthError -> "Auth/${error.reason.name} HTTP ${error.statusCode}" +
        if (error.response.errorDescription?.contains("android_key_hash", ignoreCase = true) == true) " PLATFORM_REGISTRATION" else ""
    is ApiError -> "Api/${error.reason.name} HTTP ${error.statusCode}"
    is ClientError -> "Client/${error.reason.name}"
    is java.io.IOException -> "NETWORK"
    else -> "SDK_UNKNOWN"
}
