package com.nurseflow.policy.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.policy.domain.CalendarDay
import com.nurseflow.policy.domain.MonthTarget
import com.nurseflow.policy.domain.TargetHourCalculator
import com.nurseflow.policy.domain.TargetHourPolicy
import com.nurseflow.policy.persistence.repository.ShiftTypeJdbcRepository
import com.nurseflow.policy.persistence.repository.TargetHourJdbcRepository
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class TargetHourPolicyResult(val policy: TargetHourPolicy, val policyRevision: Int)

data class HolidayCalendarResult(
    val days: List<CalendarDay>,
    val publicHolidays: List<CalendarDay>,
    val sourceConfigured: Boolean,
    val publicHolidaysFetchedAt: Instant?,
    val target: MonthTarget,
    val policyRevision: Int,
)

/**
 * Ward target-hour policy and holiday calendar (A13), admin only. Writes share the ward policy revision with shift
 * types and the consecutive-work limit: the ETag of a read must come back in If-Match, checked under the ward lock,
 * so two admins cannot overwrite each other. They do not flag drafts: hours are diagnostics computed on read,
 * not placement rules.
 */
@Service
class TargetHourPolicyService(
    private val repo: TargetHourJdbcRepository,
    private val revisions: ShiftTypeJdbcRepository,
    private val wards: WardService,
    private val audit: AuditLog,
    private val holidays: PublicHolidayService,
    transactions: PlatformTransactionManager,
) {
    private val readOnly = TransactionTemplate(transactions).apply { isReadOnly = true }

    @Transactional(readOnly = true)
    fun get(wardId: UUID, actor: String): TargetHourPolicyResult {
        wards.requireAdmin(wardId, actor)
        // Revision first: a concurrent change can only leave the ETag older than the data, never newer.
        val revision = revisions.policyRevision(wardId)
        return TargetHourPolicyResult(policyOf(wardId), revision)
    }

    @Transactional
    /** [request] is read only after the admin check, so a non-admin gets 403/404 whatever the body holds. */
    fun replace(wardId: UUID, request: () -> TargetHourPolicy, ifMatch: String, actor: String): TargetHourPolicyResult {
        wards.lockAsAdmin(wardId, actor)
        val policy = request()
        validate(policy)
        checkRevision(wardId, ifMatch)
        if (policyOf(wardId) == policy) return TargetHourPolicyResult(policy, revisions.policyRevision(wardId))
        val now = Instant.now()
        repo.savePolicy(wardId, policy, actor, now)
        revisions.bumpPolicyRevision(wardId, actor, now)
        audit.record(wardId, actor, "TARGET_HOUR_POLICY_UPDATED", wardId.toString())
        return TargetHourPolicyResult(policy, revisions.policyRevision(wardId))
    }

    @Transactional(readOnly = true)
    fun calendar(wardId: UUID, month: String, actor: String): HolidayCalendarResult {
        wards.requireAdmin(wardId, actor)
        val yearMonth = parseMonth(month)
        val revision = revisions.policyRevision(wardId)
        return calendarResult(wardId, yearMonth, revision)
    }

    @Transactional
    fun replaceCalendar(wardId: UUID, month: String, days: List<CalendarDay>, ifMatch: String, actor: String): HolidayCalendarResult {
        wards.lockAsAdmin(wardId, actor)
        val yearMonth = parseMonth(month)
        validateCalendar(yearMonth, days)
        checkRevision(wardId, ifMatch)
        val sorted = days.map { it.copy(name = it.name?.trim()?.ifEmpty { null }) }.sortedBy { it.date }
        if (repo.calendar(wardId, yearMonth) == sorted) return calendarResult(wardId, yearMonth, revisions.policyRevision(wardId))
        val now = Instant.now()
        repo.replaceCalendar(wardId, yearMonth, sorted, actor, now)
        revisions.bumpPolicyRevision(wardId, actor, now)
        audit.record(wardId, actor, "HOLIDAY_CALENDAR_UPDATED", yearMonth.toString())
        return calendarResult(wardId, yearMonth, revisions.policyRevision(wardId))
    }

    /**
     * Fetches [month] from the public holiday source, then returns the calendar. The source is called without the ward
     * lock or a transaction; on failure the stored holidays stay and the 503 tells the admin to enter them by hand.
     */
    fun refreshCalendar(wardId: UUID, month: String, actor: String): HolidayCalendarResult {
        wards.requireAdmin(wardId, actor)
        val yearMonth = parseMonth(month)
        if (holidays.refreshForAdmin(yearMonth)) audit.record(wardId, actor, "PUBLIC_HOLIDAYS_REFRESHED", yearMonth.toString())
        // One read-only transaction, so the days, holidays and target come from one state.
        return readOnly.execute { calendarResult(wardId, yearMonth, revisions.policyRevision(wardId)) }!!
    }

    /** The month's general target with the ward's current policy, its calendar and the imported holidays. */
    fun monthTarget(wardId: UUID, month: YearMonth, policy: TargetHourPolicy = policyOf(wardId)): MonthTarget =
        TargetHourCalculator.monthTarget(month, repo.calendar(wardId, month), policy.dailyStandardMinutes, repo.publicHolidays(month))

    fun policyOf(wardId: UUID): TargetHourPolicy = repo.policy(wardId) ?: TargetHourPolicy()

    private fun calendarResult(wardId: UUID, month: YearMonth, revision: Int): HolidayCalendarResult {
        return HolidayCalendarResult(
            repo.calendar(wardId, month), repo.publicHolidays(month), holidays.configured,
            repo.publicHolidayFetchedAt(month), monthTarget(wardId, month), revision,
        )
    }

    private fun validate(policy: TargetHourPolicy) {
        val valid = policy.dailyStandardMinutes in 1..MINUTES_PER_DAY &&
            policy.leaveCreditMinutes in 0..MINUTES_PER_DAY &&
            policy.halfDayCreditMinutes in 0..MINUTES_PER_DAY &&
            policy.sofCreditMinutes in 0..MINUTES_PER_DAY &&
            (policy.nightDedicatedTargetMinutes ?: 0) in 0..MAX_MONTH_MINUTES &&
            (policy.nightDedicatedNightCreditMinutes ?: 0) in 0..MINUTES_PER_DAY
        if (!valid) throw BusinessException(ErrorCode.INVALID_TARGET_HOUR_POLICY)
    }

    private fun validateCalendar(month: YearMonth, days: List<CalendarDay>) {
        val invalid = days.any { YearMonth.from(it.date) != month } ||
            days.map { it.date }.toSet().size != days.size
        if (invalid) throw BusinessException(ErrorCode.INVALID_HOLIDAY_CALENDAR)
    }

    private fun checkRevision(wardId: UUID, ifMatch: String) {
        val current = revisions.policyRevision(wardId)
        if (ifMatch != ShiftTypeService.policyEtag(current)) {
            throw BusinessException(ErrorCode.STALE_POLICY_REVISION, currentRevision = current)
        }
    }

    companion object {
        private const val MINUTES_PER_DAY = 1440
        private const val MAX_MONTH_MINUTES = 31 * MINUTES_PER_DAY

        fun parseMonth(value: String): YearMonth = try {
            if (!Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(value)) throw IllegalArgumentException()
            YearMonth.parse(value)
        } catch (_: Exception) {
            throw BusinessException(ErrorCode.INVALID_MONTH)
        }
    }
}
