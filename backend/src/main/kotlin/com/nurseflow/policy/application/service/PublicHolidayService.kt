package com.nurseflow.policy.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.policy.config.HolidayProperties
import com.nurseflow.policy.persistence.repository.TargetHourJdbcRepository
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

data class HolidayRefresh(val month: YearMonth, val holidays: Int, val fetchedAt: Instant)

/**
 * Keeps the national holiday table current. The source is called outside any transaction; only a complete,
 * parsed month replaces the stored one, so a failure or a missing key leaves earlier data and ward entries in use.
 */
@Service
class PublicHolidayService(
    private val client: PublicHolidayClient,
    private val repo: TargetHourJdbcRepository,
    private val properties: HolidayProperties,
    private val tx: TransactionTemplate,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    val configured: Boolean get() = properties.configured

    /**
     * Serialized so two refreshes of one month never interleave their replace; a write failure is reported like a
     * source failure, and the stored month stays as it was (the replace is one transaction).
     */
    @Synchronized
    fun refresh(month: YearMonth): HolidayRefresh {
        val days = client.fetch(month)
        val now = Instant.now()
        try {
            tx.executeWithoutResult { repo.replacePublicHolidays(month, days, now) }
        } catch (ex: DataAccessException) {
            log.warn("Holiday refresh could not be stored: {}", ex.javaClass.simpleName)
            throw BusinessException(ErrorCode.HOLIDAY_SOURCE_UNAVAILABLE)
        }
        return HolidayRefresh(month, days.size, now)
    }

    /**
     * An admin's "fetch now": limited to last year through next year, and a month fetched in the last
     * [MIN_ADMIN_INTERVAL] is not fetched again, so admins cannot use up the shared data.go.kr quota.
     */
    fun refreshForAdmin(month: YearMonth) {
        val now = YearMonth.now(ZoneId.of(properties.refreshZone))
        if (month.year !in (now.year - 1)..(now.year + 1)) throw BusinessException(ErrorCode.HOLIDAY_MONTH_OUT_OF_RANGE)
        if (!properties.configured) throw BusinessException(ErrorCode.HOLIDAY_SOURCE_NOT_CONFIGURED)
        val last = repo.publicHolidayFetchedAt(month)
        if (last != null && last.isAfter(Instant.now().minus(MIN_ADMIN_INTERVAL))) return
        refresh(month)
    }

    /** Current month and the next [HolidayProperties.monthsAhead] months. One failed month does not stop the rest. */
    @Scheduled(cron = "\${nurseflow.holiday.refresh-cron:0 30 3 * * *}", zone = "\${nurseflow.holiday.refresh-zone:Asia/Seoul}")
    fun scheduledRefresh() {
        if (!properties.configured) return
        val first = YearMonth.now(ZoneId.of(properties.refreshZone))
        val failed = (0..properties.monthsAhead.coerceIn(0, 24)).map { first.plusMonths(it.toLong()) }.count { month ->
            try {
                refresh(month)
                false
            } catch (ex: Exception) {
                // Any failure of one month must not stop the others; the stored month stays in use.
                if (ex !is BusinessException) log.warn("Holiday refresh of a month failed: {}", ex.javaClass.simpleName)
                true
            }
        }
        if (failed > 0) log.warn("Holiday refresh failed for {} month(s); stored holidays stay in use", failed)
    }

    private companion object {
        val MIN_ADMIN_INTERVAL: Duration = Duration.ofMinutes(10)
    }
}
