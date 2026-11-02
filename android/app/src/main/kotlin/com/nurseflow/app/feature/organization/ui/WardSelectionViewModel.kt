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
    val name: String = "",
    val timeZone: String = "",
    val nameError: String? = null,
    val timeZoneError: String? = null,
    val submitting: Boolean = false,
    /** 필드에 연결되지 않는 실패. 결과가 불확실하면 같은 입력으로 다시 시도할 수 있다. */
    val failure: ApiError? = null,
)

data class WardSelectionUiState(
    val load: LoadState = LoadState.Loading,
    val myWards: List<MyWard> = emptyList(),
    val hospitals: List<Hospital> = emptyList(),
    val selectedHospitalId: String? = null,
    val hospitalWards: List<HospitalWard> = emptyList(),
    val hospitalWardsLoading: Boolean = false,
    val hospitalWardsError: ApiError? = null,
    val form: CreateWardForm? = null,
    /** 목록에서 강조할 병동. 생성 직후에는 새 병동이다. */
    val highlightedWardId: String? = null,
    /** 사용자가 확정한 병동. 근무표 서버 연동 전까지는 선택 결과 화면만 보여준다. */
    val chosenWardId: String? = null,
    val notice: String? = null,
    val sessionExpired: Boolean = false,
) {
    val selectedHospital: Hospital? get() = hospitals.firstOrNull { it.hospitalId == selectedHospitalId }
    val isHospitalAdmin: Boolean get() = hospitals.isNotEmpty()
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

    fun refresh() {
        state.update { it.copy(load = LoadState.Loading, notice = null) }
        viewModelScope.launch { loadAll() }
    }

    fun selectHospital(hospitalId: String) {
        if (state.value.hospitals.none { it.hospitalId == hospitalId }) return
        state.update { it.copy(selectedHospitalId = hospitalId, hospitalWards = emptyList(), form = null) }
        viewModelScope.launch { loadHospitalWards(hospitalId) }
    }

    fun retryHospitalWards() {
        state.value.selectedHospitalId?.let { id -> viewModelScope.launch { loadHospitalWards(id) } }
    }

    fun highlight(wardId: String) = state.update { it.copy(highlightedWardId = wardId) }

    fun choose(wardId: String) {
        if (state.value.isMember(wardId)) state.update { it.copy(chosenWardId = wardId, highlightedWardId = wardId) }
    }

    fun changeWard() = state.update { it.copy(chosenWardId = null) }

    fun openCreateForm() {
        if (state.value.selectedHospital == null) return
        state.update { it.copy(form = CreateWardForm(), notice = null) }
    }

    fun closeCreateForm() {
        if (state.value.form?.submitting == true) return
        state.update { it.copy(form = null) }
    }

    fun editName(value: String) = state.update { s -> s.copy(form = s.form?.copy(name = value, nameError = null, failure = null)) }

    fun editTimeZone(value: String) = state.update { s -> s.copy(form = s.form?.copy(timeZone = value, timeZoneError = null, failure = null)) }

    fun submitCreate() {
        val current = state.value
        val form = current.form ?: return
        val hospital = current.selectedHospital ?: return
        if (form.submitting) return
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
        val payload = CreatePayload(hospital.hospitalId, name, zone)
        val key = submission.keyFor(payload)
        state.update { it.copy(form = form.copy(submitting = true, failure = null, nameError = null, timeZoneError = null)) }
        viewModelScope.launch {
            when (val result = repository.createWard(payload.hospitalId, payload.name, payload.timeZone, key)) {
                is ApiResult.Success -> {
                    submission.settle()
                    state.update { it.copy(form = null, highlightedWardId = result.value.wardId) }
                    loadAll(preferredHospitalId = payload.hospitalId)
                    state.update { s ->
                        val chosen = result.value.wardId.takeIf { s.isMember(it) }
                        s.copy(
                            chosenWardId = chosen ?: s.chosenWardId,
                            notice = "‘${result.value.name}’ 병동을 만들었습니다.",
                        )
                    }
                }
                is ApiResult.Failure -> onCreateFailed(result.error)
            }
        }
    }

    private fun onCreateFailed(error: ApiError) {
        if (!error.outcomeUnknown) submission.settle()
        if (error == ApiError.SessionExpired || error == ApiError.SignedOut) {
            state.update { s -> s.copy(sessionExpired = true, form = s.form?.copy(submitting = false)) }
            return
        }
        val code = (error as? ApiError.Rejected)?.code
        state.update { s ->
            val form = s.form?.copy(submitting = false) ?: return@update s
            s.copy(form = when (code) {
                "INVALID_WARD_NAME" -> form.copy(nameError = NAME_RULE)
                "WARD_NAME_TAKEN" -> form.copy(nameError = "같은 병원에 같은 이름의 병동이 있습니다.")
                "INVALID_TIME_ZONE" -> form.copy(timeZoneError = ZONE_RULE)
                else -> form.copy(failure = error)
            })
        }
        // 권한이 바뀌었을 수 있으므로 목록을 서버 기준으로 다시 맞춘다.
        if (error is ApiError.Forbidden || error is ApiError.NotFound) viewModelScope.launch { loadAll() }
    }

    private suspend fun loadAll(preferredHospitalId: String? = state.value.selectedHospitalId) {
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
            return
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
    ApiError.UnexpectedResponse -> "서버 응답을 해석하지 못했습니다. 앱을 최신 버전으로 업데이트해 주세요."
}
