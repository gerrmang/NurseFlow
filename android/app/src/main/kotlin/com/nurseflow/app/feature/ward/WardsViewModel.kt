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
    /** Wards created in this session. A reload keeps them until the server list contains them. */
    private val created = LinkedHashMap<String, MembershipDto>()
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
                created.keys.removeAll(wards.map { it.wardId }.toSet())
                mutableState.value = WardsUiState.Loaded(user.displayName, wards + created.values)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!keep) mutableState.value = WardsUiState.Failed(e.userMessage())
            }
        }
    }

    /**
     * A ward was just created (201). Show it immediately, so opening it or its invitations never
     * waits for (or loses to) the list reload, then refresh from the server without hiding it.
     */
    fun onCreated(membership: MembershipDto) {
        created[membership.wardId] = membership
        val current = mutableState.value
        mutableState.value = if (current is WardsUiState.Loaded) {
            current.copy(wards = current.wards.filterNot { it.wardId == membership.wardId } + membership)
        } else {
            WardsUiState.Loaded("", listOf(membership))
        }
        reload(silent = true)
    }
}
