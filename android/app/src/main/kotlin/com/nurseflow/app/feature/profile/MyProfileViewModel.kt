package com.nurseflow.app.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nurseflow.app.core.network.StaffMemberDto
import com.nurseflow.app.core.network.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.YearMonth

data class MyProfileUiState(
    val loading: Boolean = true,
    val member: StaffMemberDto? = null,
    val ranks: List<String> = emptyList(),
    val careerStartedOn: YearMonth? = null,
    val rank: String? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val changed: Boolean
        get() = careerStartedOn?.toString() != member?.profile?.careerStartedOn || rank != member?.profile?.rank
    val canSave: Boolean get() = careerStartedOn != null && rank != null && changed && !saving
}

class MyProfileViewModel(private val repository: ProfileRepository, private val wardId: String) : ViewModel() {
    private val _state = MutableStateFlow(MyProfileUiState())
    val state: StateFlow<MyProfileUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val member = async { repository.myProfile(wardId) }
                val policy = async { repository.policy(wardId) }
                apply(member.await(), policy.await().ranks)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.userMessage()) }
            }
        }
    }

    fun onCareerStart(value: YearMonth) = _state.update { it.copy(careerStartedOn = value, saved = false) }
    fun onRank(value: String) = _state.update { it.copy(rank = value, saved = false) }

    fun save() {
        val current = _state.value
        val start = current.careerStartedOn ?: return
        val rank = current.rank ?: return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            try {
                apply(repository.submit(wardId, start, rank), current.ranks)
                _state.update { it.copy(saved = true) }
            } catch (e: Exception) {
                _state.update { it.copy(saving = false, error = e.userMessage()) }
            }
        }
    }

    private fun apply(member: StaffMemberDto, ranks: List<String>) {
        _state.update {
            it.copy(
                loading = false, saving = false, member = member, ranks = ranks,
                careerStartedOn = member.profile.careerStartedOn?.let(YearMonth::parse),
                rank = member.profile.rank,
            )
        }
    }
}
