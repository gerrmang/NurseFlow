package com.nurseflow.app.feature.holiday

import androidx.lifecycle.ViewModel
import com.nurseflow.app.core.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.HttpException
import retrofit2.Response
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth

val holidayLabels = linkedMapOf("PUBLIC_HOLIDAY" to "공휴일", "SUBSTITUTE_HOLIDAY" to "대체공휴일", "WARD_REST_DAY" to "병동 휴무", "WARD_WORKDAY" to "근무일 지정")
val holidayPolicyLabels = listOf("하루 기준시간", "연차 환산시간", "반차 환산시간", "SOF 환산시간", "Night 전담 월 목표", "Night 전담 1회 환산시간")
fun holidayMinutes(value: Int) = "${value / 60}시간 ${value % 60}분"
fun policyValues(p: HolidayPolicyInput) = listOf(p.dailyStandardMinutes, p.leaveCreditMinutes, p.halfDayCreditMinutes, p.sofCreditMinutes, p.nightDedicatedTargetMinutes, p.nightDedicatedNightCreditMinutes).map { it?.toString() ?: "" }
fun parseHolidayPolicy(values: List<String>): HolidayPolicyInput? {
    if (values.size != 6) return null
    val parsed = values.mapIndexed { i, text ->
        if (text.isEmpty() && i >= 4) null else {
            if (!Regex("[0-9]+").matches(text)) return null
            val n = text.toIntOrNull() ?: return null
            if (n !in (if (i == 0) 1 else 0)..(if (i == 4) 44640 else 1440)) return null
            n
        }
    }
    return HolidayPolicyInput(parsed[0]!!, parsed[1]!!, parsed[2]!!, parsed[3]!!, parsed[4], parsed[5])
}
data class HolidayState(
    val month: String = YearMonth.now().toString(), val calendar: HolidayCalendar? = null, val etag: String = "",
    val days: List<HolidayDay> = emptyList(), val policy: List<String> = emptyList(),
    val calendarDirty: Boolean = false, val policyDirty: Boolean = false,
    val loading: Boolean = false, val busy: Boolean = false, val stale: Boolean = true,
    val uncertain: Boolean = false, val forbidden: Boolean = false, val notice: String? = null,
) { val editable get() = calendar != null && !loading && !busy && !stale && !uncertain && !forbidden }

/** Session-owned model holds drafts and jobs across Activity recreation. */
class HolidaySettingsViewModel(
    private val api: HolidaySettingsApi, private val wardId: String,
    private val memberships: suspend () -> List<MembershipDto>,
    private val sessionValid: () -> Boolean = { true }, private val onSessionInvalid: () -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : ViewModel(scope) {
    private val mutable = MutableStateFlow(HolidayState())
    val state = mutable.asStateFlow()
    private var active = false
    private var epoch = 0
    private var job: Job? = null
    fun activate() { if (!active) { active = true; reload() } }
    fun deactivate() { active = false; epoch++; job?.cancel(); mutable.value = HolidayState(month = state.value.month, uncertain = state.value.uncertain || state.value.busy) }
    private fun current(e: Int) = active && epoch == e && sessionValid()
    private fun invalid(): Nothing = throw ApiException(0, "INVALID_RESPONSE", "서버 응답을 확인하지 못했어요.")
    private fun <T> tagged(r: Response<T>): Pair<T, String> {
        if (!r.isSuccessful) throw HttpException(r)
        val value = r.body() ?: invalid()
        val tag = r.headers()["ETag"] ?: invalid()
        if (!Regex("\"[^\"\\r\\n]+\"").matches(tag)) invalid()
        return value to tag
    }
    private suspend fun admin(e: Int) {
        if (!current(e)) throw CancellationException()
        val wards = apiCall { memberships() }
        if (!current(e)) throw CancellationException()
        if (wards.none { it.wardId == wardId && it.isAdmin }) throw ApiException(403, "FORBIDDEN", "관리 권한이 없어요.")
    }
    private fun validate(c: HolidayCalendar, month: String) {
        if (c.month != month || c.target.month != month || c.policyRevision < 0) invalid()
        listOf(c.days, c.publicHolidays).forEachIndexed { index, days ->
            if (days.map { it.date }.toSet().size != days.size || days.size > 31) invalid()
            days.forEach { if (YearMonth.from(LocalDate.parse(it.date)).toString() != month || it.kind !in holidayLabels || (it.name?.length ?: 0) > 50 || index == 1 && it.kind !in listOf("PUBLIC_HOLIDAY", "SUBSTITUTE_HOLIDAY")) invalid() }
        }
        c.holidaySource.fetchedAt?.let { Instant.parse(it) }
        val t = c.target
        if (listOf(t.daysInMonth, t.weekendDays, t.holidayDays, t.wardRestDays, t.wardWorkdays, t.restDays, t.targetDays, t.dailyStandardMinutes, t.generalTargetMinutes).any { it < 0 }) invalid()
    }
    private fun fail(ex: Exception, write: Boolean = false, refresh: Boolean = false) {
        val p = ex as? ApiException
        if (p?.status in listOf(401, 403) || p?.code in listOf("WARD_NOT_FOUND", "USER_NOT_FOUND")) {
            mutable.value = HolidayState(month = state.value.month, forbidden = true, notice = "관리 권한 또는 세션이 변경됐어요.")
            if (p?.status == 401 || p?.code == "USER_NOT_FOUND") onSessionInvalid()
            return
        }
        val conflict = p?.status in listOf(409, 412)
        val uncertain = write && !refresh && (p == null || p.status == 0 || p.status >= 500)
        val notice = when {
            conflict -> "다른 화면에서 정책이 변경됐어요. 입력을 보관했어요. 최신 설정을 다시 조회해 주세요."
            uncertain -> "저장 결과가 불확실해요. 다시 보내지 말고 최신 설정을 조회해 확인해 주세요."
            else -> when (p?.code) {
                "HOLIDAY_SOURCE_NOT_CONFIGURED" -> "자동 공휴일이 꺼져 있어 직접 입력이 필요해요."
                "HOLIDAY_SOURCE_UNAVAILABLE" -> "가져오기에 실패했어요. 기존 달력과 입력을 유지했어요."
                "HOLIDAY_MONTH_OUT_OF_RANGE" -> "공휴일 가져오기는 작년부터 내년까지만 가능해요."
                "INVALID_TARGET_HOUR_POLICY", "INVALID_HOLIDAY_CALENDAR" -> "날짜·시간·이름 입력을 확인해 주세요."
                else -> "요청을 처리하지 못했어요. 입력을 유지했어요. 다시 조회해 주세요."
            }
        }
        mutable.value = state.value.copy(stale = state.value.stale || conflict || uncertain, uncertain = state.value.uncertain || uncertain, notice = notice)
    }
    private suspend fun load(e: Int, month: String, reset: String, expectedTag: String? = null): Boolean {
        mutable.value = state.value.copy(loading = true, stale = true)
        return try {
            admin(e)
            val (calendar, tag) = apiCall { tagged(api.calendar(wardId, month)) }
            validate(calendar, month)
            val (policy, policyTag) = apiCall { tagged(api.policy(wardId)) }
            if (parseHolidayPolicy(policyValues(policy.input())) == null) invalid()
            if (tag != policyTag || calendar.policyRevision != policy.policyRevision || expectedTag != null && tag != expectedTag) throw ApiException(412, "STALE_POLICY_REVISION", "설정이 변경됐어요.")
            admin(e)
            if (!current(e)) return false
            mutable.value = state.value.copy(month = month, calendar = calendar, etag = tag, loading = false, stale = false, forbidden = false,
                days = if (reset != "policy") calendar.days else state.value.days, calendarDirty = reset == "policy" && state.value.calendarDirty,
                policy = if (reset != "calendar") policyValues(policy.input()) else state.value.policy, policyDirty = reset == "calendar" && state.value.policyDirty)
            true
        } catch (ex: CancellationException) { throw ex }
        catch (ex: Exception) { if (current(e)) { fail(ex); mutable.value = state.value.copy(loading = false) }; false }
    }
    fun reload(month: String = state.value.month) {
        if (!active || !sessionValid() || state.value.busy) return
        if (runCatching { Regex("\\d{4}-(0[1-9]|1[0-2])").matches(month) && YearMonth.parse(month).year in 1..9999 }.getOrDefault(false).not()) {
            mutable.value = state.value.copy(notice = "대상 월은 YYYY-MM 형식의 유효한 연월로 입력해 주세요."); return
        }
        job?.cancel(); val e = ++epoch
        job = scope.launch { load(e, month, "all") }
    }
    fun editDay(day: HolidayDay, remove: Boolean = false) {
        if (!state.value.editable) return
        if (runCatching { YearMonth.from(LocalDate.parse(day.date)).toString() == state.value.month }.getOrDefault(false).not() || day.kind !in holidayLabels || (day.name?.length ?: 0) > 50) return
        mutable.value = state.value.copy(days = (state.value.days.filter { it.date != day.date } + if (remove) emptyList() else listOf(day)).sortedBy { it.date }, calendarDirty = true)
    }
    fun editPolicy(index: Int, value: String) { if (state.value.editable) mutable.value = state.value.copy(policy = state.value.policy.mapIndexed { i, s -> if (i == index) value else s }, policyDirty = true) }
    fun acknowledge() { if (!state.value.loading && !state.value.busy && !state.value.stale && !state.value.forbidden) mutable.value = state.value.copy(uncertain = false, notice = "최신 저장 결과를 확인했어요.") }
    fun save(kind: String) {
        if (!active || !sessionValid() || !state.value.editable || kind !in listOf("calendar", "policy", "refresh")) return
        val s = state.value
        val input = if (kind == "policy") parseHolidayPolicy(s.policy) ?: return else null
        val e = ++epoch
        mutable.value = s.copy(busy = true, notice = null)
        job = scope.launch {
            try {
                admin(e)
                if (kind == "refresh") {
                    val (c, tag) = apiCall { tagged(api.refresh(wardId, s.month)) }
                    validate(c, s.month)
                    if (tag != s.etag) throw ApiException(412, "STALE_POLICY_REVISION", "설정이 변경됐어요.")
                    if (current(e)) mutable.value = state.value.copy(calendar = c, notice = "국가 공휴일을 갱신했어요. 미저장 입력은 유지했어요.")
                } else {
                    val savedTag = apiCall {
                        if (kind == "calendar") { val (c, tag) = tagged(api.saveCalendar(wardId, s.month, s.etag, HolidayCalendarInput(s.days))); validate(c, s.month); tag }
                        else { val (p, tag) = tagged(api.savePolicy(wardId, s.etag, input!!)); if (parseHolidayPolicy(policyValues(p.input())) == null) invalid(); tag }
                    }
                    if (current(e) && load(e, s.month, kind, savedTag)) mutable.value = state.value.copy(notice = "저장 후 서버에서 다시 조회했어요.")
                }
            } catch (ex: CancellationException) { throw ex }
            catch (ex: Exception) { if (current(e)) fail(ex, write = true, refresh = kind == "refresh") }
            finally { if (current(e)) mutable.value = state.value.copy(busy = false) }
        }
    }
}
