package com.nurseflow.app.feature.ward

import androidx.lifecycle.ViewModel
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface WardsUiState {
    data object Loading : WardsUiState
    data class Loaded(val displayName: String, val wards: List<MembershipDto>) : WardsUiState
    data class Failed(val message: String) : WardsUiState
}

class WardsViewModel(
    private val api: NurseFlowApi,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val scope = scope
    private val mutableState = MutableStateFlow<WardsUiState>(WardsUiState.Loading)
    val state = mutableState.asStateFlow()
    private var request: Job? = null
    init { reload() }

    /** [silent]: keep showing the loaded list while refreshing, so open screens are not bounced. */
    fun reload(silent: Boolean = false) {
        request?.cancel()
        val keep = silent && mutableState.value is WardsUiState.Loaded
        if (!keep) mutableState.value = WardsUiState.Loading
        request = scope.launch {
            try {
                val user = apiCall { api.me() }
                val wards = apiCall { api.wards() }.items
                mutableState.value = WardsUiState.Loaded(user.displayName, wards)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!keep) mutableState.value = WardsUiState.Failed(e.userMessage())
            }
        }
    }

    /**
     * Add a newly created ward immediately when a complete list is already loaded. Otherwise
     * require a full reload so a failed request cannot masquerade as a complete membership list.
     */
    fun onCreated(membership: MembershipDto) {
        val current = mutableState.value
        mutableState.value = if (current is WardsUiState.Loaded) {
            current.copy(wards = current.wards.filterNot { it.wardId == membership.wardId } + membership)
        } else {
            WardsUiState.Loaded("", listOf(membership))
        }
        reload(silent = current is WardsUiState.Loaded)
    }
}
