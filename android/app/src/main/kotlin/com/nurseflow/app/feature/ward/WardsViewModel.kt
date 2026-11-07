package com.nurseflow.app.feature.ward

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WardsUiState {
    data object Loading : WardsUiState
    data class Loaded(val displayName: String, val wards: List<MembershipDto>) : WardsUiState
    data class Failed(val message: String) : WardsUiState
}

class WardsViewModel(private val api: NurseFlowApi) : ViewModel() {
    private val mutableState = MutableStateFlow<WardsUiState>(WardsUiState.Loading)
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    init { reload() }
    fun reload() {
        request?.cancel()
        mutableState.value = WardsUiState.Loading
        request = viewModelScope.launch {
            try {
                val user = apiCall { api.me() }
                val wards = apiCall { api.wards() }
                mutableState.value = WardsUiState.Loaded(user.displayName, wards.items)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = WardsUiState.Failed(e.userMessage())
            }
        }
    }
}
