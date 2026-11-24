package com.nurseflow.app.feature.staff

import androidx.lifecycle.ViewModel
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerializationException

data class StaffAdminData(val teams: List<AdminTeam>, val staff: List<AdminStaff>, val members: List<AdminMember>, val policy: AdminCareerPolicy, val eligibility: AdminEligibility?)
data class StaffAdminState(
    val data: StaffAdminData? = null, val loading: Boolean = false, val busy: Boolean = false,
    val stale: Boolean = true, val uncertain: Boolean = false, val forbidden: Boolean = false,
    val notice: String? = null, val version: Int = 0,
    val historyNurseId: String? = null, val history: List<AdminAdjustment> = emptyList(),
    val historyLoading: Boolean = false, val historyError: String? = null,
) { val editable: Boolean get() = data != null && !loading && !busy && !stale && !uncertain && !forbidden }

/** Session/ward-owned. Writes never replay automatically; every mutation resolves against a fresh snapshot. */
class StaffAdminViewModel(
    private val api: StaffAdminApi,
    private val wardId: String,
    private val memberships: suspend () -> List<MembershipDto>,
    private val sessionValid: () -> Boolean = { true },
    private val onSessionInvalid: () -> Unit = {},
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val scope = scope
    private val mutable = MutableStateFlow(StaffAdminState())
    val state: StateFlow<StaffAdminState> = mutable.asStateFlow()
    private var ticket = 0
    private var historyTicket = 0
    private var active = false
    private var epoch = 0
    private var readJob: Job? = null
    private var writeJob: Job? = null
    private var historyJob: Job? = null

    fun activate() { if (!active) { active = true; reload() } }
    fun acknowledge() {
        if (!mutable.value.stale && !mutable.value.loading && !mutable.value.busy && valid())
            mutable.value = mutable.value.copy(uncertain = false, notice = "목록을 확인했어요. 필요한 작업을 계속할 수 있어요.")
    }
    fun deactivate() {
        active = false; ++epoch; ++ticket; ++historyTicket
        readJob?.cancel(); historyJob?.cancel()
        // A sent mutation may have committed. Retain the uncertainty until next entry reload.
        val pending = mutable.value.busy
        writeJob?.cancel()
        mutable.value = StaffAdminState(uncertain = mutable.value.uncertain || pending)
    }
    private fun valid() = active && sessionValid()
    private fun deny() { ++historyTicket; historyJob?.cancel(); mutable.value = StaffAdminState(forbidden = true, notice = "병동 관리 권한이 없거나 소속이 변경됐어요.") }
    private suspend fun checkAdmin(): Boolean {
        val started = epoch
        if (!valid()) return false
        val wards = apiCall { memberships() }
        if (!valid() || started != epoch) return false
        if (wards.none { it.wardId == wardId && it.isAdmin }) { deny(); return false }
        return true
    }
    private fun failure(e: Exception): String {
        val apiError = e as? ApiException
        if (apiError?.status == 403 || apiError?.code == "WARD_NOT_FOUND") deny()
        if (apiError?.status == 401 || apiError?.code == "USER_NOT_FOUND") {
            deny(); onSessionInvalid()
        }
        return staffError(apiError?.code)
    }
    fun reload() {
        if (!valid() || mutable.value.busy) return
        readJob?.cancel()
        readJob = scope.launch { load() }
    }
    private suspend fun load(): Boolean {
        val mine = ++ticket
        ++historyTicket; historyJob?.cancel()
        mutable.value = mutable.value.copy(loading = true, stale = true, history = emptyList(), historyNurseId = null, historyLoading = false)
        return try {
            if (!checkAdmin()) return false
            val data = coroutineScope {
                val teams = async { apiCall { api.teams(wardId) }.items }
                val staff = async { apiCall { api.staff(wardId) }.items }
                val members = async { apiCall { api.members(wardId) }.items }
                val policy = async { apiCall { api.policy(wardId) } }
                val eligibility = async {
                    try {
                        apiCall {
                            val response = api.eligibility(wardId)
                            if (!response.isSuccessful) throw retrofit2.HttpException(response)
                            response.body()
                        }
                    }
                    catch (e: CancellationException) { throw e }
                    catch (e: ApiException) {
                        if (e.status == 401 || e.status == 403 || e.code == "WARD_NOT_FOUND" || e.code == "USER_NOT_FOUND") throw e
                        null
                    }
                    catch (e: SerializationException) { null }
                }
                StaffAdminData(teams.await(), staff.await(), members.await(), policy.await(), eligibility.await())
            }
            if (!valid() || mine != ticket) return false
            if (!checkAdmin()) return false
            if (!valid() || mine != ticket) return false
            mutable.value = mutable.value.copy(data = data, loading = false, stale = false, forbidden = false, version = mutable.value.version + 1)
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (valid() && mine == ticket) {
                val message = failure(e)
                mutable.value = mutable.value.copy(loading = false, stale = true, notice = message + " 최신 목록을 다시 불러와 주세요.")
            }
            false
        }
    }
    fun message(text: String) { mutable.value = mutable.value.copy(notice = text) }
    private fun write(label: String, operation: suspend () -> Unit) {
        if (!valid() || !mutable.value.editable) return
        val started = epoch
        fun current() = valid() && started == epoch
        mutable.value = mutable.value.copy(busy = true, notice = null)
        writeJob = scope.launch {
            var notice = "$label 완료. 최신 목록을 불러왔어요."
            try {
                if (!checkAdmin()) return@launch
                apiCall { operation() }
                if (!current()) return@launch
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (!current()) return@launch
                val problem = e as? ApiException
                val unknown = problem == null || problem.status == 0 || problem.status >= 500
                notice = if (unknown) "$label 결과를 확인하지 못했어요. 이미 처리됐을 수 있어요. 목록을 확인한 뒤 계속해 주세요." else failure(e)
                mutable.value = mutable.value.copy(uncertain = unknown)
            } finally {
                if (current()) mutable.value = mutable.value.copy(busy = false)
            }
            if (!current() || mutable.value.forbidden) return@launch
            // Hold busy across the refresh as well; no writes can use a stale response.
            mutable.value = mutable.value.copy(busy = true)
            try {
                val loaded = load()
                if (current() && !mutable.value.forbidden) mutable.value = mutable.value.copy(notice = notice + if (loaded) "" else " 최신 목록 확인 전에는 편집할 수 없어요.")
            } finally { if (current()) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun saveTeam(id: String?, name: String, order: String) {
        val n = order.toIntOrNull()
        if (name.trim().length !in 1..50 || n == null || n !in 1..1000) { message("팀 이름 1~50자와 표시 순서 1~1000을 입력해 주세요."); return }
        write("팀 저장") { val input = AdminTeamInput(name.trim(), n); if (id == null) api.createTeam(wardId, input) else api.updateTeam(wardId, id, input) }
    }
    fun deleteTeam(team: AdminTeam) = write("팀 삭제") { api.deleteTeam(wardId, team.teamId) }
    fun saveStaff(staff: AdminStaff?, name: String, qualifications: String, active: Boolean) {
        val q = staffQualifications(staff, qualifications)
        if (name.trim().length !in 1..100 || q.size > 50 || q.any { it.length > 64 }) { message("이름 1~100자, 자격은 각 64자 이내·50개 이하로 입력해 주세요."); return }
        write("간호사 기록 저장") { val input = AdminStaffInput(name.trim(), q, active); if (staff == null) api.createStaff(wardId, input) else api.updateStaff(wardId, staff.nurseId, input) }
    }
    fun assignTeam(nurseId: String, teamId: String?) = write("팀 배치") { api.assignTeam(wardId, nurseId, AdminTeamAssignment(teamId)) }
    fun saveOrder(teamId: String, nurseIds: List<String>) {
        val expected = mutable.value.data?.staff?.filter { it.active && it.team?.teamId == teamId }?.map { it.nurseId } ?: return
        if (nurseIds.size != expected.size || nurseIds.toSet() != expected.toSet() || nurseIds.size > 200) { message("팀의 활성 간호사 전체를 확인한 뒤 다시 정렬해 주세요."); return }
        write("경력순 저장") { api.order(wardId, teamId, AdminTeamOrder(nurseIds)) }
    }
    fun saveProfile(id: String, month: String, rank: String) {
        if (!Regex("^(?!0000)\\d{4}-(0[1-9]|1[0-2])$").matches(month) || rank !in (mutable.value.data?.policy?.ranks ?: emptyList())) { message("실제 경력 시작 연월(YYYY-MM)과 병동 직급을 확인해 주세요."); return }
        write("실제 경력 저장") { api.profile(wardId, id, AdminProfileInput(month, rank)) }
    }
    fun confirmProfile(id: String) = write("실제 경력 확인") { api.confirmProfile(wardId, id) }
    fun saveSettings(id: String, senior: Boolean, stage: Int?) {
        if (stage != null && stage !in 1..5) return
        write("편성 정보 저장") { api.settings(wardId, id, AdminRosterSettings(senior, stage)) }
    }
    fun membership(id: String, input: AdminMembershipInput) = write("구성원 변경") { api.membership(wardId, id, input) }
    fun history(id: String?) {
        historyJob?.cancel(); val mine = ++historyTicket
        val canRead = id != null && valid() && !mutable.value.stale && !mutable.value.busy
        mutable.value = mutable.value.copy(historyNurseId = id, history = emptyList(), historyLoading = canRead,
            historyError = if (id != null && !canRead) "최신 목록을 불러온 뒤 이력을 다시 조회해 주세요." else null)
        if (!canRead) return
        historyJob = scope.launch {
            try {
                if (!checkAdmin()) return@launch
                val items = apiCall { api.adjustments(wardId, id) }.items
                if (valid() && mine == historyTicket) mutable.value = mutable.value.copy(history = items, historyLoading = false)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (valid() && mine == historyTicket) { val msg = failure(e); mutable.value = mutable.value.copy(historyLoading = false, historyError = msg) } }
        }
    }
}

internal fun staffQualifications(staff: AdminStaff?, text: String): List<String> =
    // A comma is valid within a server qualification. Do not parse an untouched display value.
    if (staff != null && text == staff.qualifications.joinToString(", ")) staff.qualifications
    else text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.distinct()

internal fun staffError(code: String?): String = when (code) {
    "TEAM_NAME_DUPLICATE" -> "같은 이름의 팀이 있어요. 다른 이름을 입력해 주세요."
    "TEAM_NOT_EMPTY" -> "활성 간호사가 있는 팀은 삭제할 수 없어요. 먼저 팀을 옮겨 주세요."
    "TEAM_ORDER_STALE" -> "팀 구성이 바뀌었어요. 최신 목록을 확인하고 다시 정렬해 주세요."
    "LAST_WARD_ADMIN" -> "마지막 활성 관리자는 해제하거나 비활성화할 수 없어요. 다른 관리자를 먼저 지정해 주세요."
    "MEMBER_ROLE_REQUIRED" -> "관리자 전용 계정은 관리자 역할만 해제할 수 없어요. 접근을 막으려면 비활성화해 주세요."
    "ADMIN_NURSE_RECORD_CONFLICT" -> "관리자와 활성 간호사 기록은 함께 사용할 수 없어요. 간호사 기록 비활성화 또는 관리자 역할 해제가 먼저 필요해요."
    "MEMBER_INACTIVE" -> "구성원 접근을 먼저 재활성화한 뒤 간호사 기록을 활성화해 주세요."
    "INVALID_CAREER_START" -> "실제 경력 시작 연월을 확인해 주세요. 미래 연월은 사용할 수 없어요."
    "RANK_NOT_IN_POLICY" -> "현재 병동 직급 목록에서 다시 선택해 주세요."
    "PROFILE_NOT_SUBMITTED" -> "실제 경력 정보를 먼저 입력해 주세요."
    "TEAM_NOT_FOUND", "NURSE_NOT_FOUND", "MEMBER_NOT_FOUND" -> "대상 기록이 변경됐어요. 최신 목록을 확인해 주세요."
    else -> "요청을 처리하지 못했어요. 입력값·연결 상태·권한을 확인해 주세요."
}
