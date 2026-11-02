package com.nurseflow.app.feature.ward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nurseflow.app.core.network.MembershipDto
import com.nurseflow.app.core.network.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateWardUiState(
    val name: String = "",
    val code: String = "",
    val busy: Boolean = false,
    val error: String? = null,
    val created: MembershipDto? = null,
) {
    val canSubmit: Boolean get() = name.isNotBlank() && name.length <= 100 && code.isNotBlank() && !busy
}

class CreateWardViewModel(private val repository: WardRepository) : ViewModel() {
    private val _state = MutableStateFlow(CreateWardUiState())
    val state: StateFlow<CreateWardUiState> = _state.asStateFlow()

    fun onName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onCode(value: String) = _state.update { it.copy(code = value, error = null) }

    fun submit() {
        val current = _state.value
        if (!current.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val ward = repository.createWard(current.name, current.code)
                _state.update { it.copy(busy = false, created = ward) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.userMessage()) }
            }
        }
    }
}
