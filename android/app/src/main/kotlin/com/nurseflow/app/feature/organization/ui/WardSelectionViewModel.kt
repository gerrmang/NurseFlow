package com.nurseflow.app.feature.organization.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nurseflow.app.core.network.ApiError
import com.nurseflow.app.core.network.ApiResult
import com.nurseflow.app.core.network.outcomeUnknown
import com.nurseflow.app.feature.organization.domain.Hospital
import com.nurseflow.app.feature.organization.domain.HospitalWard
import com.nurseflow.app.feature.organization.domain.MyWard
import com.nurseflow.app.feature.organization.domain.OrganizationRepository
import com.nurseflow.app.feature.organization.domain.WardNameRules
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DateTimeException
import java.time.ZoneId
import java.util.UUID

sealed interface LoadState {
    data object Loading : LoadState
    data object Ready : LoadState
    data class Failed(val error: ApiError) : LoadState
}

data class CreateWardForm(
    /** 이 양식이 속한 병원. 생성 응답은 이 병원의 양식에만 반영한다. */
    val hospitalId: String,
    val name: String = "",
    val timeZone: String = "",
    val nameError: String? = null,
    val timeZoneError: String? = null,
    val submitting: Boolean = false,
    /** 필드에 연결되지 않는 실패. 결과가 불확실하면 같은 입력으로 다시 시도할 수 있다. */
    val failure: ApiError? = null,
)

/**
 * 병동 생성 작업의 단계. POST부터 성공 뒤 목록 갱신·새 병동 선택까지 하나의 작업으로 묶어 잠근다.
 * 양식(`CreateWardForm.submitting`)과 독립적이므로 성공 직후 양식이 닫혀도 잠금이 유지된다.
 */
sealed interface CreateOperation {
    /** 생성 요청을 보내고 응답을 기다리는 중. */
    data object Submitting : CreateOperation

    /** 생성 응답 뒤 목록을 서버 기준으로 다시 맞추는 중. 성공했다면 [created]를 마지막에 선택한다. */
    data class Reconciling(val created: HospitalWard?) : CreateOperation
}

data class WardSelectionUiState(
    val load: LoadState = LoadState.Loading,
    /** 진행 중인 병동 생성 작업. null이면 작업이 없다. */
    val createOperation: CreateOperation? = null,
    /** 생성은 성공했지만 목록 갱신에 실패한 병동. [WardSelectionViewModel.refresh]가 생성 POST 없이 목록만 다시 읽고 선택한다. */
    val createdPendingRefresh: HospitalWard? = null,
    val myWards: List<MyWard> = emptyList(),
    val hospitals: List<Hospital> = emptyList(),
    val selectedHospitalId: String? = null,
    val hospitalWards: List<HospitalWard> = emptyList(),
    val hospitalWardsLoading: Boolean = false,
    val hospitalWardsError: ApiError? = null,
    val form: CreateWardForm? = null,
    /** 목록에서 강조할 병동. 소속 병동이 하나면 그 병동, 생성 직후에는 새 병동이다. */
    val highlightedWardId: String? = null,
    /** 이 화면에서 방금 만든 병동. 강조와 별개로 "방금 생성" 표시에만 쓴다. */
    val justCreatedWardId: String? = null,
    /** 사용자가 확정한 병동. 근무표 서버 연동 전까지는 선택 결과 화면만 보여준다. */
    val chosenWardId: String? = null,
    val notice: String? = null,
    /** [notice]가 실패 안내인가. 권한이 사라져 생성 양식이 닫힌 경우처럼 이유를 알려야 할 때 쓴다. */
    val noticeIsError: Boolean = false,
    val sessionExpired: Boolean = false,
) {
    val selectedHospital: Hospital? get() = hospitals.firstOrNull { it.hospitalId == selectedHospitalId }
    val isHospitalAdmin: Boolean get() = hospitals.isNotEmpty()

    /**
     * 병동 생성 작업(요청·성공 뒤 목록 갱신·선택)이 진행 중인가.
     * 이 동안 병원 전환·양식 열기/닫기·입력 변경·병동 선택·새로고침·재제출을 막는다.
     */
    val isCreating: Boolean get() = createOperation != null
    val chosenWard: MyWard? get() = myWards.firstOrNull { it.wardId == chosenWardId }
    fun isMember(wardId: String) = myWards.any { it.wardId == wardId }
}

/**
 * 병원·병동 선택과 병동 생성 화면 상태. 서버 응답만 사용하며 DemoRoster 데이터와 섞지 않는다.
 * 권한 판정은 서버가 하고, 화면은 서버가 돌려준 목록과 오류 코드로만 버튼과 안내를 정한다.
 */
class WardSelectionViewModel(
    private val repository: OrganizationRepository,
    newKey: () -> String = { UUID.randomUUID().toString() },
) : ViewModel() {
    private val state = MutableStateFlow(WardSelectionUiState())
    val uiState: StateFlow<WardSelectionUiState> = state.asStateFlow()
    private val submission = IdempotentSubmission<CreatePayload>(newKey)

    init {
        refresh()
    }

    /**
     * 목록을 다시 읽는다. 생성은 성공했지만 목록 갱신에 실패한 병동이 있으면 생성 요청을 다시 보내지 않고
     * 같은 잠금 아래에서 목록을 다시 읽은 뒤 그 병동을 선택한다.
     */
    fun refresh() {
        if (state.value.isCreating) return
        val pending = state.value.createdPendingRefresh
        state.update { it.copy(load = LoadState.Loading, notice = null, noticeIsError = false) }
        if (pending != null) {
            launchCreateOperation(CreateOperation.Reconciling(pending)) { finishCreated(pending) }
        } else {
            viewModelScope.launch { loadAll() }
        }
    }

    /**
     * 생성 요청 중에는 병원을 바꾸지 않는다. 바꾸면 진행 중인 요청의 응답이 다른 병원의 양식·선택 상태와
     * 공용 멱등 키에 섞일 수 있다(PR #14 리뷰 P2). 화면도 같은 동안 병원 카드를 비활성화한다.
     */
    fun selectHospital(hospitalId: String) {
        if (state.value.isCreating) return
        if (state.value.hospitals.none { it.hospitalId == hospitalId }) return
        state.update { it.copy(selectedHospitalId = hospitalId, hospitalWards = emptyList(), form = null) }
        viewModelScope.launch { loadHospitalWards(hospitalId) }
    }

    fun retryHospitalWards() {
        state.value.selectedHospitalId?.let { id -> viewModelScope.launch { loadHospitalWards(id) } }
    }

    fun highlight(wardId: String) = state.update { it.copy(highlightedWardId = wardId) }

    fun choose(wardId: String) {
        if (state.value.isCreating) return
        if (state.value.isMember(wardId)) state.update { it.copy(chosenWardId = wardId, highlightedWardId = wardId) }
    }

    fun changeWard() = state.update { it.copy(chosenWardId = null) }

    fun openCreateForm() {
        if (state.value.isCreating) return
        val hospital = state.value.selectedHospital ?: return
        state.update { it.copy(form = CreateWardForm(hospitalId = hospital.hospitalId), notice = null, noticeIsError = false) }
    }

    fun closeCreateForm() {
        if (state.value.isCreating) return
        state.update { it.copy(form = null) }
    }

    fun editName(value: String) {
        if (state.value.isCreating) return
        state.update { s -> s.copy(form = s.form?.copy(name = value, nameError = null, failure = null)) }
    }

    fun editTimeZone(value: String) {
        if (state.value.isCreating) return
        state.update { s -> s.copy(form = s.form?.copy(timeZone = value, timeZoneError = null, failure = null)) }
    }

    fun submitCreate() {
        val current = state.value
        if (current.isCreating) return
        val form = current.form ?: return
        val hospital = current.selectedHospital ?: return
        if (form.submitting || form.hospitalId != hospital.hospitalId) return
        val name = WardNameRules.normalize(form.name)
        val zone = form.timeZone.trim().ifEmpty { null }
        val zoneValid = zone == null || try { ZoneId.of(zone); true } catch (_: DateTimeException) { false }
        if (name == null || !zoneValid) {
            state.update { s ->
                s.copy(form = form.copy(
                    nameError = if (name == null) NAME_RULE else null,
                    timeZoneError = if (!zoneValid) ZONE_RULE else null,
                ))
            }
            return
        }
        val payload = CreatePayload(form.hospitalId, name, zone)
        val key = submission.keyFor(payload)
        state.update { it.copy(form = form.copy(submitting = true, failure = null, nameError = null, timeZoneError = null)) }
        launchCreateOperation(CreateOperation.Submitting) {
            when (val result = repository.createWard(payload.hospitalId, payload.name, payload.timeZone, key)) {
                is ApiResult.Success -> {
                    submission.settle()
                    val created = result.value
                    // 양식은 닫지만 잠금은 목록 갱신·선택이 끝날 때까지 유지한다.
                    state.update { s ->
                        s.copy(
                            createOperation = CreateOperation.Reconciling(created),
                            form = s.form?.takeUnless { it.hospitalId == payload.hospitalId },
                            highlightedWardId = created.wardId,
                            justCreatedWardId = created.wardId,
                        )
                    }
                    finishCreated(created)
                }
                is ApiResult.Failure -> onCreateFailed(result.error, payload.hospitalId)
            }
        }
    }

    /**
     * 생성 작업을 시작한다. 잠금은 코루틴이 시작되기 전에 걸고, 성공·실패·예외·취소 어떤 경로로 끝나도
     * `finally`에서 해제한다.
     */
    private fun launchCreateOperation(operation: CreateOperation, block: suspend () -> Unit) {
        state.update { it.copy(createOperation = operation) }
        viewModelScope.launch {
            var completed = false
            try {
                block()
                completed = true
            } finally {
                state.update { s ->
                    // 갱신 도중 예외로 끝났다면 이미 생성된 병동을 잊지 않고 목록 재시도로 마무리할 수 있게 남긴다.
                    val unfinished = if (completed) null else (s.createOperation as? CreateOperation.Reconciling)?.created
                    s.copy(
                        createOperation = null,
                        createdPendingRefresh = unfinished ?: s.createdPendingRefresh,
                        form = s.form?.copy(submitting = false),
                    )
                }
            }
        }
    }

    /** 생성된 병동을 목록에 반영하고 선택한다. 목록 갱신에 실패하면 생성 POST 없이 목록만 다시 시도하도록 남긴다. */
    private suspend fun finishCreated(created: HospitalWard) {
        val loaded = loadAll(preferredHospitalId = created.hospitalId)
        state.update { s ->
            if (!loaded) return@update s.copy(createdPendingRefresh = created)
            s.copy(
                createdPendingRefresh = null,
                chosenWardId = created.wardId.takeIf { s.isMember(it) } ?: s.chosenWardId,
                highlightedWardId = created.wardId,
                justCreatedWardId = created.wardId,
                notice = "‘${created.name}’ 병동을 만들었습니다.",
                noticeIsError = false,
            )
        }
    }

    private suspend fun onCreateFailed(error: ApiError, hospitalId: String) {
        // 결과 불명(통신 실패·5xx·해석할 수 없는 2xx)이면 같은 입력의 재시도가 같은 키를 쓰도록 남긴다.
        if (!error.outcomeUnknown) submission.settle()
        if (error == ApiError.SessionExpired || error == ApiError.SignedOut) {
            state.update { s -> s.copy(sessionExpired = true, form = s.form?.ownedBy(hospitalId)?.copy(submitting = false) ?: s.form) }
            return
        }
        val code = (error as? ApiError.Rejected)?.code
        state.update { s ->
            // 요청을 보낸 병원의 양식에만 결과를 반영한다.
            val form = s.form?.ownedBy(hospitalId)?.copy(submitting = false) ?: return@update s
            s.copy(form = when (code) {
                "INVALID_WARD_NAME" -> form.copy(nameError = NAME_RULE)
                "WARD_NAME_TAKEN" -> form.copy(nameError = "같은 병원에 같은 이름의 병동이 있습니다.")
                "INVALID_TIME_ZONE" -> form.copy(timeZoneError = ZONE_RULE)
                else -> form.copy(failure = error)
            })
        }
        // 권한이 바뀌었을 수 있으므로 목록을 서버 기준으로 다시 맞춘다. 다시 읽은 결과 병원 관리 권한이 없어져
        // 생성 양식이 닫히더라도 사용자가 이유를 알 수 있도록 안내를 남긴다.
        // 이 목록 갱신도 같은 생성 작업 잠금 아래에서 끝까지 기다린다(늦게 끝난 갱신이 사용자의 전환을 덮지 않게).
        if (error is ApiError.Forbidden || error is ApiError.NotFound) {
            state.update {
                it.copy(
                    createOperation = CreateOperation.Reconciling(null),
                    notice = "${error.userMessage()} 병동 목록을 다시 불러왔습니다.",
                    noticeIsError = true,
                )
            }
            loadAll(preferredHospitalId = hospitalId)
        }
    }

    /** 본인 병동·병원 목록을 다시 읽는다. 둘 다 성공하면 true. 실패 내용은 [LoadState.Failed]로 남긴다. */
    private suspend fun loadAll(preferredHospitalId: String? = state.value.selectedHospitalId): Boolean {
        val (wards, hospitals) = coroutineScope {
            val wards = async { repository.myWards() }
            val hospitals = async { repository.myHospitals() }
            wards.await() to hospitals.await()
        }
        val failure = listOf(wards, hospitals).filterIsInstance<ApiResult.Failure>().firstOrNull()?.error
        if (failure != null) {
            state.update {
                it.copy(
                    load = LoadState.Failed(failure),
                    sessionExpired = failure == ApiError.SessionExpired || failure == ApiError.SignedOut,
                )
            }
            return false
        }
        val myWards = (wards as ApiResult.Success).value
        val myHospitals = (hospitals as ApiResult.Success).value
        val hospitalId = preferredHospitalId?.takeIf { id -> myHospitals.any { it.hospitalId == id } }
            ?: myHospitals.firstOrNull()?.hospitalId
        state.update { s ->
            s.copy(
                load = LoadState.Ready,
                myWards = myWards,
                hospitals = myHospitals,
                selectedHospitalId = hospitalId,
                hospitalWards = if (hospitalId == null) emptyList() else s.hospitalWards,
                chosenWardId = s.chosenWardId?.takeIf { id -> myWards.any { it.wardId == id } },
                highlightedWardId = s.highlightedWardId ?: myWards.singleOrNull()?.wardId,
                form = if (hospitalId == null) null else s.form,
                sessionExpired = false,
            )
        }
        hospitalId?.let { loadHospitalWards(it) }
        return true
    }

    private suspend fun loadHospitalWards(hospitalId: String) {
        state.update { it.copy(hospitalWardsLoading = true, hospitalWardsError = null) }
        val result = repository.hospitalWards(hospitalId)
        state.update { s ->
            if (s.selectedHospitalId != hospitalId) return@update s.copy(hospitalWardsLoading = false)
            when (result) {
                is ApiResult.Success -> s.copy(hospitalWards = result.value, hospitalWardsLoading = false)
                is ApiResult.Failure -> s.copy(
                    hospitalWardsLoading = false,
                    hospitalWardsError = result.error,
                    sessionExpired = s.sessionExpired || result.error == ApiError.SessionExpired,
                )
            }
        }
    }

    private fun CreateWardForm.ownedBy(hospitalId: String): CreateWardForm? = takeIf { it.hospitalId == hospitalId }

    private data class CreatePayload(val hospitalId: String, val name: String, val timeZone: String?)

    companion object {
        const val NAME_RULE = "병동 이름은 1~100자로 입력해 주세요."
        const val ZONE_RULE = "시간대는 Asia/Seoul 같은 IANA 이름이어야 합니다."
    }
}

/** 화면에 보여줄 안전한 안내. 서버의 `detail` 문자열 대신 안정적인 분류로 정한다. */
fun ApiError.userMessage(): String = when (this) {
    ApiError.NotConfigured -> "이 빌드에는 서버 주소가 설정되지 않았습니다."
    ApiError.SignedOut -> "로그인이 필요합니다."
    ApiError.SessionExpired -> "로그인이 만료되었습니다. 다시 로그인해 주세요."
    is ApiError.Forbidden -> if (code == "HOSPITAL_ADMIN_REQUIRED") "병원 관리자 권한이 없습니다." else "이 작업을 할 권한이 없습니다."
    is ApiError.NotFound -> if (code == "HOSPITAL_NOT_FOUND") "병원을 찾을 수 없거나 접근할 수 없습니다." else "대상을 찾을 수 없거나 접근할 수 없습니다."
    is ApiError.Rejected -> "요청을 처리할 수 없습니다. 입력을 확인해 주세요."
    ApiError.Network -> "서버에 연결하지 못했습니다. 네트워크를 확인한 뒤 다시 시도해 주세요."
    is ApiError.Server -> "서버에서 요청을 완료하지 못했습니다. 잠시 뒤 다시 시도해 주세요."
    ApiError.UnexpectedResponse -> "서버 응답을 확인하지 못했습니다."
}
