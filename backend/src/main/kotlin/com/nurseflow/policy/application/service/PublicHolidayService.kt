package com.nurseflow.policy.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.policy.config.HolidayProperties
import com.nurseflow.policy.persistence.repository.TargetHourJdbcRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
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

    fun refresh(month: YearMonth): HolidayRefresh {
        val days = client.fetch(month)
        val now = Instant.now()
        tx.executeWithoutResult { repo.replacePublicHolidays(month, days, now) }
        return HolidayRefresh(month, days.size, now)
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
            } catch (_: BusinessException) {
                true
            }
        }
        if (failed > 0) log.warn("Holiday refresh failed for {} month(s); stored holidays stay in use", failed)
    }
}
