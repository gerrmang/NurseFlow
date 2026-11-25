package com.nurseflow.app.feature.schedule

import androidx.lifecycle.ViewModel
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

data class ScheduleTagged<T>(val value: T, val etag: String)
data class ScheduleData(
    val shifts: ScheduleTagged<ScheduleShifts>, val teams: List<AdminTeam>, val staff: List<AdminStaff>,
    val draft: ScheduleTagged<ScheduleRoster>?, val absences: List<ScheduleAbsence>,
)
data class ScheduleState(
    val month: String = YearMonth.now().toString(), val data: ScheduleData? = null,
    val loading: Boolean = false, val busy: Boolean = false, val stale: Boolean = true,
    val uncertain: Boolean = false, val forbidden: Boolean = false, val notice: String? = null,
    val version: Int = 0, val preview: SchedulePreview? = null,
    val nurseId: String? = null, val pattern: String? = null,
) { val editable get() = data != null && !loading && !busy && !stale && !uncertain && !forbidden }

/** One session/ward. Mutations are serialized and never retried after an uncertain response. */
class ScheduleSettingsViewModel(
    private val api: ScheduleSettingsApi, private val wardId: String,
    private val memberships: suspend () -> List<MembershipDto>,
    private val sessionValid: () -> Boolean = { true }, private val onSessionInvalid: () -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val mutable = MutableStateFlow(ScheduleState())
    val state: StateFlow<ScheduleState> = mutable.asStateFlow()
    private var active = false
    private var epoch = 0
    private var job: Job? = null
    private var previewInput: Pair<String, ScheduleShiftInput>? = null
    fun activate() { if (!active) { active = true; reload() } }
    fun deactivate() {
        active = false; epoch++; job?.cancel(); previewInput = null
        mutable.value = ScheduleState(month = state.value.month, uncertain = state.value.uncertain || state.value.busy)
    }
    private fun current(e: Int) = active && sessionValid() && epoch == e
    private fun invalid(): Nothing = throw ApiException(0, "INVALID_RESPONSE", "서버 응답을 확인하지 못했어요.")
    private fun <T> body(r: Response<T>): T {
        if (!r.isSuccessful) throw HttpException(r)
        return r.body() ?: invalid()
    }
    private fun <T> tagged(r: Response<T>): ScheduleTagged<T> {
        val v = body(r)
        val tag = r.headers()["ETag"] ?: invalid()
        if (!Regex("\"[^\"\\r\\n]+\"").matches(tag)) invalid()
        return ScheduleTagged(v, tag)
    }
    private fun failure(e: Exception): String {
        val p = e as? ApiException
        if (p?.status == 401 || p?.status == 403 || p?.code in listOf("WARD_NOT_FOUND", "USER_NOT_FOUND")) {
            mutable.value = state.value.copy(data = null, stale = true, forbidden = true, pattern = null, preview = null)
            if (p?.status == 401 || p?.code == "USER_NOT_FOUND") onSessionInvalid()
        }
        return when (p?.code) {
            "STALE_POLICY_REVISION", "STALE_ROSTER_REVISION" -> "다른 화면에서 변경됐어요. 최신 값을 확인하고 다시 입력해 주세요."
            "ASSIGNMENTS_EXIST" -> "이미 배치된 초안의 수요는 바꿀 수 없어요. 배치 편집에서 먼저 해제해야 해요."
            "DRAFT_EXISTS", "NOT_DRAFT" -> "초안 상태가 변경됐어요. 최신 초안을 확인해 주세요."
            "ABSENCE_ASSIGNMENT_OVERLAP" -> "기존 배치와 승인 부재가 겹쳐요. 먼저 배치를 해제해 주세요."
            "SHIFT_TYPE_CODE_DUPLICATE" -> "비활성 유형을 포함해 같은 근무 코드가 있어요."
            "INVALID_SHIFT_MINUTES", "INVALID_SHIFT_INTERVAL", "SHIFT_SPANS_MIDNIGHT_MISMATCH" -> "근무 시각·자정 경과·휴게·인정·환산 시간을 확인해 주세요."
            "TEAM_REQUIRED_FOR_DEMAND", "INVALID_REQUIREMENTS", "DUPLICATE_REQUIREMENT", "TOO_MANY_SLOTS", "DATE_OUTSIDE_ROSTER" -> "팀·날짜·근무 유형·필요 인원과 중복 항목을 확인해 주세요."
            "INVALID_ABSENCE", "ABSENCE_OUTSIDE_ROSTER" -> "부재 구간과 선택한 초안의 월을 확인해 주세요."
            else -> p?.message ?: "요청을 처리하지 못했어요. 최신 값을 다시 조회해 주세요."
        }
    }
    private suspend fun checkAdmin(e: Int) {
        if (!current(e)) throw CancellationException()
        val wards = apiCall { memberships() }
        if (!current(e)) throw CancellationException()
        if (wards.none { it.wardId == wardId && it.isAdmin }) throw ApiException(403, "FORBIDDEN", "병동 관리 권한이 없어요.")
    }
    private fun validateRoster(r: ScheduleRoster, full: Boolean = false) {
        if (r.wardId != wardId || r.month != state.value.month || r.revision < 1 || r.status !in listOf("DRAFT", "PUBLISHED")) invalid()
        ZoneId.of(r.timeZone)
        if (full && r.slots == null) invalid()
    }
    private suspend fun load(e: Int): Boolean {
        mutable.value = state.value.copy(loading = true, stale = true, preview = null, pattern = null)
        previewInput = null
        return try {
            checkAdmin(e)
            val data = apiCall {
                val shifts = tagged(api.shifts(wardId))
                if (shifts.value.policyRevision < 0) invalid()
                val teams = body(api.teams(wardId)).items
                val staff = body(api.staff(wardId)).items
                val rosters = body(api.rosters(wardId, state.value.month)).items
                rosters.forEach { validateRoster(it) }
                val drafts = rosters.filter { it.status == "DRAFT" }
                if (drafts.size > 1) invalid()
                var draft: ScheduleTagged<ScheduleRoster>? = null
                var absences = emptyList<ScheduleAbsence>()
                drafts.firstOrNull()?.let { summary ->
                    val before = tagged(api.roster(summary.rosterId))
                    absences = body(api.absences(summary.rosterId)).items
                    draft = tagged(api.roster(summary.rosterId))
                    validateRoster(draft!!.value, true)
                    if (draft!!.value.rosterId != summary.rosterId || before.etag != draft!!.etag || draft!!.value.status != "DRAFT")
                        throw ApiException(412, "STALE_ROSTER_REVISION", "초안이 변경됐어요.")
                    absences.forEach { if (it.kind !in listOf("APPROVED_LEAVE", "APPROVED_HALF_DAY") || Instant.parse(it.startsAt) >= Instant.parse(it.endsAt)) invalid() }
                }
                ScheduleData(shifts, teams, staff, draft, absences)
            }
            checkAdmin(e)
            if (!current(e)) return false
            val selectedNurse = state.value.nurseId?.takeIf { id -> data.staff.any { it.nurseId == id } }
            val pattern = selectedNurse?.let { id ->
                apiCall { body(api.pattern(wardId, id)) }.workPattern.also {
                    if (it !in listOf("GENERAL", "DAY_EVENING_ONLY", "NIGHT_ONLY")) invalid()
                }
            }
            if (!current(e)) return false
            mutable.value = state.value.copy(data = data, nurseId = selectedNurse, pattern = pattern, loading = false, stale = false, forbidden = false, version = state.value.version + 1)
            true
        } catch (ex: CancellationException) { throw ex }
        catch (ex: Exception) {
            if (current(e)) { val msg = failure(ex); mutable.value = state.value.copy(loading = false, stale = true, notice = msg) }
            false
        }
    }
    fun reload() {
        if (!active || !sessionValid() || state.value.busy) return
        job?.cancel(); val e = ++epoch
        job = scope.launch { load(e) }
    }
    fun setMonth(month: String) {
        if (state.value.busy || state.value.uncertain) return
        if (!Regex("\\d{4}-(0[1-9]|1[0-2])").matches(month) || runCatching { YearMonth.parse(month).year in 1..9999 }.getOrDefault(false).not()) {
            message("대상 월은 YYYY-MM 형식의 유효한 연월로 입력해 주세요."); return
        }
        mutable.value = state.value.copy(month = month, data = null, notice = null); reload()
    }
    fun acknowledge() { if (!state.value.stale && !state.value.busy && !state.value.loading) mutable.value = state.value.copy(uncertain = false, notice = "서버의 최신 결과를 확인했어요.") }
    fun message(text: String) { mutable.value = state.value.copy(notice = text) }
    fun clearPreview() { previewInput = null; mutable.value = state.value.copy(preview = null) }
    private fun run(label: String, operation: suspend () -> Unit) {
        if (!state.value.editable || !active || !sessionValid()) return
        job?.cancel(); val e = ++epoch
        mutable.value = state.value.copy(busy = true, preview = null, notice = null)
        previewInput = null
        job = scope.launch {
            var msg = "$label 완료. 서버에서 다시 조회한 값이에요."
            try { checkAdmin(e); apiCall { operation() } }
            catch (ex: CancellationException) { throw ex }
            catch (ex: Exception) {
                if (!current(e)) return@launch
                val p = ex as? ApiException
                val uncertain = p == null || p.status == 0 || p.status >= 500
                msg = failure(ex)
                if (uncertain) msg = "저장 결과를 확인하지 못했어요. 이미 처리됐을 수 있어요. 최신 결과를 확인하기 전에는 다시 보내지 마세요."
                mutable.value = state.value.copy(uncertain = uncertain)
            }
            if (!current(e)) return@launch
            if (!state.value.forbidden) load(e)
            if (current(e)) mutable.value = state.value.copy(busy = false, notice = if (state.value.forbidden) "관리 권한 또는 세션이 변경됐어요." else msg + if (state.value.stale) " 최신 값 재조회도 실패했어요." else "")
        }
    }
    fun preview(id: String, input: ScheduleShiftInput) {
        if (!state.value.editable) return
        val e = ++epoch
        mutable.value = state.value.copy(busy = true, preview = null); previewInput = null
        job = scope.launch {
            try {
                checkAdmin(e)
                val result = apiCall { body(api.preview(wardId, id, input)) }
                if (!current(e)) return@launch
                if (result.policyRevision != state.value.data?.shifts?.value?.policyRevision) throw ApiException(412, "STALE_POLICY_REVISION", "설정이 변경됐어요.")
                previewInput = id to input
                mutable.value = state.value.copy(busy = false, preview = result)
            } catch (ex: CancellationException) { throw ex }
            catch (ex: Exception) {
                if (current(e)) {
                    val msg = failure(ex)
                    if (!state.value.forbidden) load(e)
                    if (current(e)) mutable.value = state.value.copy(busy = false, notice = msg)
                }
            }
        }
    }
    fun saveShift(id: String?, input: ScheduleShiftInput) {
        val data = state.value.data ?: return
        if (id != null && (previewInput != (id to input) || state.value.preview == null)) { message("변경 영향을 먼저 확인해 주세요."); return }
        run("근무 유형 저장") {
            if (id == null) tagged(api.createShift(wardId, data.shifts.etag, UUID.randomUUID().toString(), input))
            else tagged(api.updateShift(wardId, id, data.shifts.etag, input))
        }
    }
    fun deactivateShift(id: String) { val data = state.value.data ?: return; run("비활성화") { tagged(api.deactivateShift(wardId, id, data.shifts.etag)) } }
    fun createDraft() { val month = state.value.month; if (state.value.data?.draft != null) return; run("초안 생성") { tagged(api.createRoster(wardId, UUID.randomUUID().toString(), ScheduleMonth(month))) } }
    fun replaceRequirements(items: List<ScheduleRequirement>) {
        val draft = state.value.data?.draft ?: return
        if (draft.value.staffing.assigned > 0 || draft.value.slots.orEmpty().any { it.status == "FILLED" }) { message("기존 배치를 먼저 해제해야 해요."); return }
        run("수요 전체 교체") { tagged(api.requirements(draft.value.rosterId, draft.etag, ScheduleRequirements(items))) }
    }
    fun saveAbsence(id: String?, input: ScheduleAbsenceInput) {
        val draft = state.value.data?.draft ?: return
        run("승인 부재 저장") {
            if (id == null) tagged(api.createAbsence(draft.value.rosterId, draft.etag, UUID.randomUUID().toString(), input))
            else tagged(api.updateAbsence(draft.value.rosterId, id, draft.etag, input))
        }
    }
    fun deleteAbsence(id: String) {
        val draft = state.value.data?.draft ?: return
        run("승인 부재 삭제") { val r = api.deleteAbsence(draft.value.rosterId, id, draft.etag); if (r.code() != 204) { if (!r.isSuccessful) throw HttpException(r); invalid() } }
    }
    fun selectNurse(id: String) {
        if (!state.value.editable) return
        val e = ++epoch
        mutable.value = state.value.copy(nurseId = id, pattern = null, busy = true)
        job = scope.launch {
            try {
                checkAdmin(e)
                val p = apiCall { body(api.pattern(wardId, id)) }.workPattern
                if (p !in listOf("GENERAL", "DAY_EVENING_ONLY", "NIGHT_ONLY")) invalid()
                if (current(e)) mutable.value = state.value.copy(pattern = p, busy = false)
            } catch (ex: CancellationException) { throw ex }
            catch (ex: Exception) { if (current(e)) { val msg = failure(ex); mutable.value = state.value.copy(busy = false, notice = msg) } }
        }
    }
    fun savePattern(pattern: String) {
        val id = state.value.nurseId ?: return
        if (state.value.pattern == null || pattern !in listOf("GENERAL", "DAY_EVENING_ONLY", "NIGHT_ONLY")) return
        run("근무 형태 저장") {
            val p = body(api.savePattern(wardId, id, SchedulePattern(pattern)))
            if (p.workPattern != pattern) invalid()
            val reloaded = body(api.pattern(wardId, id))
            if (reloaded.workPattern !in listOf("GENERAL", "DAY_EVENING_ONLY", "NIGHT_ONLY")) invalid()
        }
    }
}
