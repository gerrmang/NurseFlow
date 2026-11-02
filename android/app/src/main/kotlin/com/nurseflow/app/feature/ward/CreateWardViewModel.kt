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
import java.time.YearMonth

data class CreateWardUiState(
    val name: String = "",
    val careerStartedOn: YearMonth? = null,
    val rank: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    val created: MembershipDto? = null,
) {
    /** Ward name plus the admin's own career start and rank are all required. */
    val canSubmit: Boolean
        get() = name.isNotBlank() && name.length <= 100 && careerStartedOn != null && rank != null && !busy
}

class CreateWardViewModel(private val repository: WardRepository) : ViewModel() {
    private val _state = MutableStateFlow(CreateWardUiState())
    val state: StateFlow<CreateWardUiState> = _state.asStateFlow()

    fun onName(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onCareerStart(value: YearMonth) = _state.update { it.copy(careerStartedOn = value, error = null) }
    fun onRank(value: String) = _state.update { it.copy(rank = value, error = null) }

    fun submit() {
        val current = _state.value
        val start = current.careerStartedOn ?: return
        val rank = current.rank ?: return
        if (!current.canSubmit) return
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val ward = repository.createWard(current.name, start, rank)
                _state.update { it.copy(busy = false, created = ward) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, error = e.userMessage()) }
            }
        }
    }
}
