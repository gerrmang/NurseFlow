package com.nurseflow.app.feature.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nurseflow.app.core.network.InvitationPreviewDto
import com.nurseflow.app.core.network.MembershipDto
import com.nurseflow.app.core.network.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class JoinUiState(
    val input: String = "",
    val token: String? = null,
    val preview: InvitationPreviewDto? = null,
    val careerStartedOn: YearMonth? = null,
    val rank: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val joined: MembershipDto? = null,
) {
    val isMemberInvite: Boolean get() = preview?.role == "WARD_MEMBER"
    /** Profile is optional at join, but if one field is chosen the other is required. */
    val canJoin: Boolean get() = preview != null && !busy && ((careerStartedOn == null) == (rank == null))
}

/** Invitation code → preview (ward/role) → optional career profile → join. */
class JoinWardViewModel(private val repository: InvitationRepository, initialCode: String?) : ViewModel() {
    private val _state = MutableStateFlow(JoinUiState(input = initialCode.orEmpty()))
    val state: StateFlow<JoinUiState> = _state.asStateFlow()

    init {
        if (initialCode != null) checkCode()
    }

    fun onInput(value: String) = _state.update { it.copy(input = value, error = null) }
    fun onCareerStart(value: YearMonth) = _state.update { it.copy(careerStartedOn = value) }
    fun onRank(value: String) = _state.update { it.copy(rank = value) }

    fun checkCode() {
        val token = InviteCode.extract(_state.value.input)
        if (token == null) {
            _state.update { it.copy(error = "초대 코드를 찾지 못했어요. 링크 전체를 다시 붙여 넣어 주세요.") }
            return
        }
        _state.update { it.copy(busy = true, error = null, token = token) }
        viewModelScope.launch {
            try {
                val preview = repository.preview(token)
                _state.update { it.copy(preview = preview, busy = false) }
            } catch (e: Exception) {
                _state.update { it.copy(preview = null, busy = false, error = e.userMessage()) }
            }
        }
    }

    fun join() {
        val current = _state.value
        val token = current.token ?: return
        if (!current.canJoin) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val profileStart = current.careerStartedOn.takeIf { current.isMemberInvite }
                val profileRank = current.rank.takeIf { current.isMemberInvite }
                val membership = repository.redeem(token, profileStart, profileRank)
                _state.update { it.copy(busy = false, joined = membership) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.userMessage()) }
            }
        }
    }

    fun reset() = _state.update { JoinUiState(input = it.input) }
}
