package com.nurseflow.offrequest.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/**
 * Ward settings for preferred days off (decision 15, 2026-11-24): at most [monthlyLimit] open requests per nurse
 * per month (default 4), submitted until the end of day [deadlineDay] of the month before (default the 15th).
 */
data class OffRequestPolicy(val monthlyLimit: Int, val deadlineDay: Int) {
    init {
        require(monthlyLimit in LIMIT_RANGE && deadlineDay in DEADLINE_DAY_RANGE)
    }

    /** Exclusive: the request window for [month] closes at midnight after the deadline day, in the ward's zone. */
    fun deadline(month: YearMonth, zone: ZoneId): Instant =
        month.minusMonths(1).atDay(deadlineDay).plusDays(1).atStartOfDay(zone).toInstant()

    companion object {
        const val DEFAULT_MONTHLY_LIMIT = 4
        const val DEFAULT_DEADLINE_DAY = 15
        val LIMIT_RANGE = 0..31
        /** Every month has day 28, so one setting is valid for every month. */
        val DEADLINE_DAY_RANGE = 1..28
    }
}
