package com.nurseflow.app.feature.ward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nurseflow.app.core.network.CurrentUserDto
import com.nurseflow.app.core.network.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WardsUiState {
    data object Loading : WardsUiState
    data class Loaded(val user: CurrentUserDto) : WardsUiState
    data class Failed(val message: String) : WardsUiState
}

class WardsViewModel(private val repository: WardRepository) : ViewModel() {
    private val _state = MutableStateFlow<WardsUiState>(WardsUiState.Loading)
    val state: StateFlow<WardsUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        _state.value = WardsUiState.Loading
        viewModelScope.launch {
            _state.value = try {
                WardsUiState.Loaded(repository.me())
            } catch (e: Exception) {
                WardsUiState.Failed(e.userMessage())
            }
        }
    }
}
