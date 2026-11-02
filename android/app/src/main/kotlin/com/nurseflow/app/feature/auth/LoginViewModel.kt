package com.nurseflow.app.feature.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nurseflow.app.core.network.userMessage
import com.nurseflow.app.core.session.SessionManager
import com.nurseflow.app.feature.invite.InviteCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val pendingInvite: Boolean = false,
)

class LoginViewModel(
    private val session: SessionManager,
    private val kakao: KakaoLoginClient,
) : ViewModel() {
    private val _state = MutableStateFlow(LoginUiState(pendingInvite = session.pendingInviteCode != null))
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /** Session state switches to SignedIn on success; the root navigation reacts to that. */
    fun login(activityContext: Context) {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val nonce = kakao.newNonce()
                val idToken = kakao.login(activityContext, nonce)
                session.loginWithKakao(idToken, nonce)
            } catch (_: KakaoLoginCancelled) {
                // User closed Kakao login: no error message.
            } catch (e: KakaoLoginFailed) {
                _state.update { it.copy(error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.userMessage()) }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /** Remembers an invitation code entered before login. Returns false if it is not a valid code. */
    fun rememberInvite(input: String): Boolean {
        val code = InviteCode.extract(input) ?: return false
        session.pendingInviteCode = code
        _state.update { it.copy(pendingInvite = true) }
        return true
    }
}
