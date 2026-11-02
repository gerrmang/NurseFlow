package com.nurseflow.app.feature.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nurseflow.app.core.network.CreatedInvitationDto
import com.nurseflow.app.core.network.InvitationDto
import com.nurseflow.app.core.network.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class InvitesUiState(
    val role: String = "WARD_MEMBER",
    val expiresInHours: Int = 72,
    val creating: Boolean = false,
    /** Shown once after creation: the code cannot be fetched again later. */
    val created: CreatedInvitationDto? = null,
    val invitations: List<InvitationDto> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class InvitesViewModel(private val repository: InvitationRepository, private val wardId: String) : ViewModel() {
    private val _state = MutableStateFlow(InvitesUiState())
    val state: StateFlow<InvitesUiState> = _state.asStateFlow()

    init { refresh() }

    fun onRole(role: String) = _state.update { it.copy(role = role) }
    fun onExpiry(hours: Int) = _state.update { it.copy(expiresInHours = hours) }
    fun dismissCreated() = _state.update { it.copy(created = null) }

    fun refresh() {
        viewModelScope.launch {
            try {
                val list = repository.list(wardId)
                _state.update { it.copy(invitations = list, loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun create() {
        val current = _state.value
        if (current.creating) return
        _state.update { it.copy(creating = true, error = null) }
        viewModelScope.launch {
            try {
                val created = repository.create(wardId, current.role, current.expiresInHours)
                _state.update { it.copy(creating = false, created = created) }
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(creating = false, error = e.userMessage()) }
            }
        }
    }

    fun revoke(invitationId: String) {
        viewModelScope.launch {
            try {
                repository.revoke(wardId, invitationId)
                refresh()
            } catch (e: Exception) {
                _state.update { it.copy(error = e.userMessage()) }
            }
        }
    }
}
