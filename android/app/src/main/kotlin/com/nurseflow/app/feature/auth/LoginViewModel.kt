package com.nurseflow.app.feature.auth
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nurseflow.app.core.network.userMessage
import com.nurseflow.app.core.session.SessionManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(val loading: Boolean = false, val error: String? = null)

class LoginViewModel(private val session: SessionManager, private val kakao: KakaoLoginClient) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val state = mutableState.asStateFlow()
    fun login(context: Context) {
        if (state.value.loading) return
        mutableState.value = LoginUiState(loading = true)
        viewModelScope.launch {
            try {
                val nonce = kakao.newNonce()
                session.loginWithKakao(kakao.login(context, nonce), nonce)
                mutableState.value = LoginUiState()
            } catch (e: CancellationException) {
                throw e
            } catch (_: KakaoLoginCancelled) {
                mutableState.value = LoginUiState()
            } catch (e: Exception) {
                mutableState.value = LoginUiState(error = if (e is KakaoLoginFailed) e.message else e.userMessage())
            }
        }
    }
}
