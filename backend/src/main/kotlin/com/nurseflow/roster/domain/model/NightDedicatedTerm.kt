package com.nurseflow.roster.domain.model

import java.time.Instant
import java.time.YearMonth
import java.util.UUID

enum class NightDedicatedTermStatus { ACTIVE, SHORTENED, CANCELLED }

/**
 * One nurse's Night-dedicated period (A15), whole months from [startMonth] to [endMonth] inclusive. Ending early
 * keeps the row and moves [endMonth] back; [plannedEndMonth] keeps what was first recorded. An [endMonth] before
 * [startMonth] means the term was cancelled before it began and covers no month.
 */
data class NightDedicatedTerm(
    val id: UUID,
    val wardId: UUID,
    val nurseId: UUID,
    val startMonth: YearMonth,
    val plannedEndMonth: YearMonth,
    val endMonth: YearMonth,
    val createdAt: Instant,
    val createdBy: String,
    val endedAt: Instant? = null,
    val endedBy: String? = null,
) {
    val status: NightDedicatedTermStatus
        get() = when {
            endMonth < startMonth -> NightDedicatedTermStatus.CANCELLED
            endMonth < plannedEndMonth -> NightDedicatedTermStatus.SHORTENED
            else -> NightDedicatedTermStatus.ACTIVE
        }

    fun covers(month: YearMonth): Boolean = month in startMonth..endMonth

    fun overlaps(other: NightDedicatedTerm): Boolean =
        status != NightDedicatedTermStatus.CANCELLED && other.status != NightDedicatedTermStatus.CANCELLED &&
            startMonth <= other.endMonth && other.startMonth <= endMonth
}
