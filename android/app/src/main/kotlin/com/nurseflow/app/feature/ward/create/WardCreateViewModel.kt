package com.nurseflow.app.feature.ward.create

import androidx.lifecycle.ViewModel
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class CreateStep { SEARCH, NAME, DONE }

sealed interface SearchState {
    data object Idle : SearchState
    data class Loading(val query: String) : SearchState
    data class Results(val query: String, val items: List<HospitalCandidateDto>, val totalCount: Int, val attribution: String?) : SearchState {
        /** More matches exist than the one page shown: ask for a narrower query. */
        val truncated: Boolean get() = totalCount > items.size
    }
    data class Empty(val query: String, val attribution: String?) : SearchState
    data class Failed(val failure: WardCreateFailure) : SearchState
}

data class WardCreateState(
    val step: CreateStep = CreateStep.SEARCH,
    val queryInput: String = "",
    val queryError: String? = null,
    val search: SearchState = SearchState.Idle,
    val selected: HospitalCandidateDto? = null,
    val wardName: String = "",
    val creating: Boolean = false,
    val createError: WardCreateFailure? = null,
    val created: MembershipDto? = null,
    /** A create response was lost: the ward may already exist on the server. */
    val lostResponse: Boolean = false,
    /** Input differs from the lost request, so the next attempt is a new request (a new key). */
    val inputChangedSinceLoss: Boolean = false,
    /**
     * Hospitals the server answered HOSPITAL_NOT_FOUND for during this visit ("source:externalId").
     * They stay visible in old results but cannot be picked again; a reset forgets them.
     */
    val invalidHospitals: Set<String> = emptySet(),
) {
    fun isInvalid(hospital: HospitalCandidateDto) = hospitalKey(hospital) in invalidHospitals
}

private fun hospitalKey(hospital: HospitalCandidateDto) = "${hospital.source}:${hospital.externalId}"

/**
 * Hospital search and ward creation for one visit of the create flow.
 *
 * One fixed-key instance is kept by the session's ViewModel store: rotation keeps its state, and
 * [reset] on every entry and exit starts clean. Every response carries the generation it was
 * requested in; a response from before a [reset] (search or create) is discarded.
 *
 * Idempotency-Key: one key is bound to one [Draft] (hospital reference + trimmed ward name,
 * the same content the server hashes). An unchanged retry after a lost response reuses the key,
 * so a ward created by the first attempt is replayed instead of duplicated; any change makes a
 * new key. The key lives in memory only and is never logged. Nothing is retried automatically.
 */
class WardCreateViewModel(
    private val api: WardCreationApi,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
    /** Ends the local session when the server no longer knows the account (USER_NOT_FOUND). */
    private val onSessionInvalid: () -> Unit = {},
    /** Called once with the created membership so the ward list can show it immediately. */
    private val onCreated: (MembershipDto) -> Unit = {},
    private val newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel(scope) {
    private data class Draft(val source: String, val externalId: String, val wardName: String)

    private val scope = scope
    private val mutable = MutableStateFlow(WardCreateState())
    val state: StateFlow<WardCreateState> = mutable.asStateFlow()
    private var searchJob: Job? = null
    private var createJob: Job? = null
    private var searchTicket = 0
    private var epoch = 0
    private var keyedDraft: Draft? = null
    private var key: String? = null

    /** Starts a fresh visit: drops all state and the key/draft, and invalidates requests in flight. */
    fun reset() {
        searchJob?.cancel()
        createJob?.cancel()
        searchTicket++
        epoch++
        keyedDraft = null
        key = null
        mutable.value = WardCreateState()
    }

    /** All exit paths refresh memberships before forgetting an uncertain creation result. */
    fun leave(refreshWards: () -> Unit) {
        refreshWards()
        reset()
    }

    fun setQuery(text: String) { update { it.copy(queryInput = text, queryError = null) } }

    fun search() {
        val hint = validateHospitalQuery(mutable.value.queryInput)
        if (hint != null) { update { it.copy(queryError = hint) }; return }
        val query = mutable.value.queryInput.trim()
        searchJob?.cancel()
        val mine = ++searchTicket
        // A new search explicitly ends the "hospital not found" notice; other errors are unrelated to it.
        update {
            it.copy(
                queryError = null,
                search = SearchState.Loading(query),
                createError = if (it.createError == WardCreateFailure.HOSPITAL_NOT_FOUND) null else it.createError,
            )
        }
        searchJob = scope.launch {
            val result = try {
                apiCall { api.searchHospitals(query) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mine != searchTicket) return@launch
                val failure = e.toWardCreateFailure()
                update { it.copy(search = SearchState.Failed(failure)) }
                if (failure.endsSession) onSessionInvalid()
                return@launch
            }
            if (mine != searchTicket) return@launch // a newer search or a reset owns the screen now
            update {
                it.copy(
                    search = if (result.items.isEmpty()) SearchState.Empty(query, result.attribution)
                    else SearchState.Results(query, result.items, result.totalCount, result.attribution),
                )
            }
        }
    }

    fun select(candidate: HospitalCandidateDto) {
        if (mutable.value.creating || mutable.value.created != null) return
        if (mutable.value.isInvalid(candidate)) return // the server already refused this hospital
        update { it.copy(selected = candidate, step = CreateStep.NAME, createError = it.createError.keepIfLimit()) }
    }

    fun backToSearch() {
        if (mutable.value.creating || mutable.value.created != null) return
        update { it.copy(selected = null, step = CreateStep.SEARCH, createError = it.createError.keepIfLimit()) }
    }

    fun setWardName(text: String) {
        if (mutable.value.creating) return
        update { it.copy(wardName = text, createError = it.createError.keepIfLimit()) }
    }

    fun create() {
        val s = mutable.value
        if (s.creating || s.created != null || s.createError == WardCreateFailure.LIMIT_REACHED) return
        val selected = s.selected ?: return
        val name = s.wardName.trim()
        if (name.isEmpty()) { update { it.copy(createError = WardCreateFailure.INVALID_REQUEST) }; return }
        if (name.length > 100) { update { it.copy(createError = WardCreateFailure.NAME_TOO_LONG) }; return }
        val draft = Draft(selected.source, selected.externalId, name)
        if (draft != keyedDraft || key == null) {
            keyedDraft = draft
            key = newKey()
        }
        val useKey = key!!
        val mine = epoch
        update { it.copy(creating = true, createError = null) }
        createJob = scope.launch {
            val membership = try {
                apiCall { api.createWard(useKey, CreateWardBody(HospitalRefBody(draft.source, draft.externalId), draft.wardName)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mine != epoch) return@launch
                fail(e.toWardCreateFailure())
                return@launch
            }
            if (mine != epoch) return@launch
            keyedDraft = null
            key = null
            update {
                it.copy(creating = false, createError = null, created = membership, step = CreateStep.DONE, lostResponse = false)
            }
            onCreated(membership)
        }
    }

    private fun fail(failure: WardCreateFailure) {
        // A definite refusal leaves no replay record, so keeping the key is harmless. A reused
        // key means the server saw different content for it: start over with a new one.
        if (failure == WardCreateFailure.KEY_REUSED) {
            keyedDraft = null
            key = null
        }
        update {
            it.copy(
                creating = false,
                createError = failure,
                lostResponse = if (failure == WardCreateFailure.KEY_REUSED) false else it.lostResponse || failure.outcomeUnknown,
                selected = if (failure == WardCreateFailure.HOSPITAL_NOT_FOUND) null else it.selected,
                invalidHospitals = if (failure == WardCreateFailure.HOSPITAL_NOT_FOUND && it.selected != null) {
                    it.invalidHospitals + hospitalKey(it.selected)
                } else it.invalidHospitals,
                step = if (failure == WardCreateFailure.HOSPITAL_NOT_FOUND) CreateStep.SEARCH else it.step,
            )
        }
        if (failure.endsSession) onSessionInvalid()
    }

    private fun WardCreateFailure?.keepIfLimit() = if (this == WardCreateFailure.LIMIT_REACHED) this else null

    private fun currentDraft(s: WardCreateState): Draft? =
        s.selected?.let { Draft(it.source, it.externalId, s.wardName.trim()) }

    /** Applies [block], then recomputes whether the input still matches an unresolved lost request. */
    private fun update(block: (WardCreateState) -> WardCreateState) {
        val next = block(mutable.value)
        val changed = next.lostResponse && keyedDraft != null && currentDraft(next) != keyedDraft
        mutable.value = next.copy(inputChangedSinceLoss = changed)
    }
}
