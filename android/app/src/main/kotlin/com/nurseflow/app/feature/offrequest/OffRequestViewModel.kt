package com.nurseflow.app.feature.offrequest

import androidx.lifecycle.ViewModel
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

data class OffRequestData(val list: OffRequestList, val policy: OffPolicySnapshot?, val eligibility: OffEligibility?)
data class OffRequestState(
    val month: YearMonth, val data: OffRequestData? = null, val loading: Boolean = false,
    val busy: Boolean = false, val stale: Boolean = true, val uncertain: Boolean = false,
    val forbidden: Boolean = false, val notice: String? = null, val version: Int = 0,
) {
    val editable get() = data != null && !loading && !busy && !stale && !uncertain && !forbidden
    val canMoveMonth get() = !busy && !uncertain && !forbidden
}

/** Kept in the session's ViewModelStore. Leaving cancels reads and retains unknown write outcomes. */
class OffRequestViewModel(
    private val api: OffRequestApi,
    private val wardId: String,
    val admin: Boolean,
    private val memberships: suspend () -> List<MembershipDto>,
    private val sessionValid: () -> Boolean = { true },
    private val onSessionInvalid: () -> Unit = {},
    private val clock: Clock = Clock.systemDefaultZone(),
    initialMonth: YearMonth = YearMonth.now(clock),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val scope = scope
    private val mutable = MutableStateFlow(OffRequestState(initialMonth))
    val state = mutable.asStateFlow()
    private var active = false
    private var epoch = 0
    private var ticket = 0
    private var readJob: Job? = null
    private var writeJob: Job? = null
    private fun valid() = active && sessionValid()
    fun activate() { if (!active) { active = true; reload() } }
    fun deactivate() {
        active = false; ++epoch; ++ticket
        readJob?.cancel(); writeJob?.cancel()
        mutable.value = OffRequestState(mutable.value.month, uncertain = mutable.value.uncertain || mutable.value.busy)
    }
    fun message(text: String) { mutable.value = mutable.value.copy(notice = text) }
    fun acknowledge() {
        val s = mutable.value
        if (valid() && !s.stale && !s.busy && !s.loading && !s.forbidden)
            mutable.value = s.copy(uncertain = false, notice = "최신 목록을 확인했어요. 필요한 작업을 계속해 주세요.")
    }
    private fun deny() {
        mutable.value = OffRequestState(mutable.value.month, forbidden = true, notice = "병동 소속·역할이 변경됐어요. 병동 목록에서 다시 확인해 주세요.")
    }
    private suspend fun checkAccess(): Boolean {
        val started = epoch
        if (!valid()) return false
        val ward = apiCall { memberships() }.firstOrNull { it.wardId == wardId }
        if (!valid() || started != epoch) return false
        if (ward == null || ward.isAdmin != admin || (!admin && "WARD_MEMBER" !in ward.roles)) { deny(); return false }
        return true
    }
    private fun failure(e: Exception): String {
        val p = e as? ApiException
        if (p?.status == 401 || p?.code == "USER_NOT_FOUND") { deny(); onSessionInvalid() }
        else if (p?.status == 403 && p.code == "NURSE_IDENTITY_REQUIRED")
            mutable.value = mutable.value.copy(data = null, stale = true, forbidden = false)
        else if (p?.status == 403 || p?.code == "WARD_NOT_FOUND") deny()
        return offRequestError(p?.code)
    }
    fun reload() {
        if (!valid() || mutable.value.busy) return
        readJob?.cancel()
        readJob = scope.launch { load() }
    }
    fun changeMonth(delta: Long) {
        if (!valid() || !mutable.value.canMoveMonth || delta !in -1L..1L) return
        val month = mutable.value.month.plusMonths(delta)
        if (month.year !in 1..9999) return
        mutable.value = OffRequestState(month, version = mutable.value.version + 1)
        reload()
    }
    private suspend fun load(): Boolean {
        val mine = ++ticket
        val month = mutable.value.month
        mutable.value = mutable.value.copy(loading = true, stale = true)
        return try {
            if (!checkAccess()) return false
            val data = apiCall {
                suspend fun snapshot(): OffRequestData {
                    val list = api.list(wardId, month.toString()).checked(month)
                    val policy = if (admin) api.policy(wardId).checkedPolicy() else null
                    val eligible = if (admin) api.eligibility(wardId).checked() else null
                    return OffRequestData(list, policy, eligible)
                }
                var data = snapshot()
                // Another administrator may save between these reads. Retry reads only, once.
                if (data.policy != null && data.policy.value.monthlyLimit != data.list.monthlyLimit) data = snapshot()
                if (data.policy != null && data.policy.value.monthlyLimit != data.list.monthlyLimit)
                    throw ApiException(409, "POLICY_SNAPSHOT_CHANGED", "Policy changed during read")
                data
            }
            if (!valid() || mine != ticket || !checkAccess()) return false
            if (!valid() || mine != ticket) return false
            mutable.value = mutable.value.copy(data = data, loading = false, stale = false, forbidden = false, version = mutable.value.version + 1)
            true
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (valid() && mine == ticket) {
                val text = failure(e)
                mutable.value = mutable.value.copy(loading = false, stale = true, notice = text + if ((e as? ApiException)?.code == "NURSE_IDENTITY_REQUIRED") "" else " 최신 목록을 다시 불러와 주세요.")
            }
            false
        }
    }
    private fun write(label: String, operation: suspend () -> Unit) {
        if (!valid() || !mutable.value.editable) return
        val started = epoch
        fun current() = valid() && epoch == started
        mutable.value = mutable.value.copy(busy = true, notice = null)
        writeJob = scope.launch {
            var sent = false
            var notice = "$label 완료. 최신 목록을 불러왔어요."
            try {
                if (!checkAccess()) return@launch
                sent = true
                apiCall { operation() }
            } catch (e: CancellationException) {
                if (current()) mutable.value = mutable.value.copy(busy = false, stale = true, uncertain = sent)
                throw e
            }
            catch (e: Exception) {
                if (!current()) return@launch
                val p = e as? ApiException
                val unknown = sent && (p == null || p.status == 0 || p.status >= 500)
                notice = if (unknown) "$label 결과를 확인하지 못했어요. 이미 처리됐을 수 있어요. 최신 목록을 확인해 주세요." else failure(e)
                mutable.value = mutable.value.copy(uncertain = unknown)
            } finally {
                // busy remains set through load; early permission/session exits cannot leave a lock behind.
                if (current() && mutable.value.forbidden) mutable.value = mutable.value.copy(busy = false)
            }
            if (!current() || mutable.value.forbidden) return@launch
            try {
                val loaded = load()
                if (current() && !mutable.value.forbidden)
                    mutable.value = mutable.value.copy(notice = notice + if (loaded) "" else " 최신 조회 전에는 편집할 수 없어요.")
            } finally { if (current()) mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun submit(date: String, nurseId: String? = null) {
        if (!mutable.value.editable) return
        val day = runCatching { LocalDate.parse(date) }.getOrNull()
        if (day == null || day.toString() != date || YearMonth.from(day) != mutable.value.month) { message("선택한 월의 날짜를 골라 주세요."); return }
        val data = mutable.value.data ?: return
        if (admin && data.eligibility?.items?.none { it.nurseId == nurseId && it.eligible } != false) { message("대리 입력할 편성 대상 간호사를 선택해 주세요."); return }
        if (!admin && nurseId != null) return
        if (!admin && ownCount(data.list) >= data.list.monthlyLimit) { message(offRequestError("OFF_REQUEST_LIMIT_REACHED")); return }
        if (data.list.items.any { it.date == date && it.holdsDate && (!admin || it.nurseId == nurseId) }) { message(offRequestError("OFF_REQUEST_DUPLICATE")); return }
        write(if (admin) "대리 입력" else "신청") {
            val result = api.submit(wardId, OffRequestInput(date, nurseId)).checked()
            require(result.date == date && result.status == "SUBMITTED" && result.source == if (admin) "ADMIN" else "NURSE")
            require(!admin || result.nurseId == nurseId)
        }
    }
    fun decide(id: String, action: String) {
        val item = mutable.value.data?.list?.items?.firstOrNull { it.requestId == id } ?: return
        if (!canDecide(item, action)) return
        write(when (action) { "confirm" -> "확정"; "reject" -> "반려"; else -> "철회" }) {
            val result = when (action) { "confirm" -> api.confirm(wardId, id); "reject" -> api.reject(wardId, id); else -> api.withdraw(wardId, id) }.checked()
            val expected = when (action) { "confirm" -> "CONFIRMED"; "reject" -> "REJECTED"; else -> "WITHDRAWN" }
            require(result.requestId == id && result.nurseId == item.nurseId && result.date == item.date && result.status == expected)
        }
    }
    fun canDecide(item: OffRequest, action: String, now: Instant = clock.instant()): Boolean {
        if (!mutable.value.editable) return false
        return when (action) {
            "confirm", "reject" -> admin && item.status == "SUBMITTED"
            "withdraw" -> if (admin) item.holdsDate else item.status == "SUBMITTED" && now.isBefore(Instant.parse(mutable.value.data!!.list.deadline))
            else -> false
        }
    }
    fun savePolicy(limit: String, day: String) {
        if (!admin || !mutable.value.editable) return
        val l = limit.toIntOrNull(); val d = day.toIntOrNull()
        if (l == null || l !in 0..31 || d == null || d !in 1..28) { message("월 한도 0~31일과 전달 마감일 1~28일을 입력해 주세요."); return }
        val policy = mutable.value.data?.policy ?: return
        write("정책 저장") {
            val result = api.savePolicy(wardId, policy.etag, OffRequestPolicyInput(l, d)).checkedPolicy()
            require(result.value.monthlyLimit == l && result.value.deadlineDay == d)
        }
    }
}

fun ownCount(list: OffRequestList) = list.items.count { it.source == "NURSE" && it.holdsDate }
fun offRequestError(code: String?): String = when (code) {
    "OFF_REQUEST_DUPLICATE" -> "같은 날짜에 신청 또는 확정된 희망 오프가 있어요. 최신 목록을 확인해 주세요."
    "OFF_REQUEST_LIMIT_REACHED" -> "이번 달 본인 신청 한도에 도달했어요. 관리자에게 문의해 주세요."
    "OFF_REQUEST_DEADLINE_PASSED" -> "마감 뒤에는 본인이 철회할 수 없어요. 관리자에게 문의해 주세요."
    "OFF_REQUEST_STATE_CONFLICT", "OFF_REQUEST_NOT_FOUND" -> "신청 상태가 변경됐어요. 최신 목록을 확인하고 다시 선택해 주세요."
    "STALE_POLICY_REVISION" -> "다른 관리자가 정책을 변경했어요. 최신 설정을 확인하고 다시 저장해 주세요."
    "POLICY_SNAPSHOT_CHANGED" -> "조회 중 정책이 다시 변경됐어요. 새로고침해 최신 설정을 확인해 주세요."
    "NURSE_IDENTITY_REQUIRED" -> "연결된 간호사 기록이 없어 희망 오프를 이용할 수 없어요. 관리자에게 문의해 주세요."
    "NURSE_NOT_FOUND" -> "간호사 기록이 변경됐어요. 최신 대상을 확인해 주세요."
    "NETWORK" -> "서버에 연결할 수 없어요. 연결 상태를 확인해 주세요."
    "INVALID_REQUEST" -> "날짜와 입력값을 확인해 주세요. 본인은 지난 날짜에 신청할 수 없어요."
    else -> "요청을 처리하지 못했어요. 연결 상태·권한·최신 목록을 확인해 주세요."
}
