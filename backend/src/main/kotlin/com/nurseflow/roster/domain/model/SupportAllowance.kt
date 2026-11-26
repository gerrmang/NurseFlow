package com.nurseflow.roster.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A ward admin's permission for one nurse to work on another team's slots (A14).
 * [startDate]..[endDate] is inclusive and compared with the slot's work date (a Night belongs to the day it
 * starts). An empty [shiftTypeIds] covers every shift type. A revoked allowance permits nothing but is kept
 * as history.
 */
data class SupportAllowance(
    val id: UUID,
    val wardId: UUID,
    val nurseId: UUID,
    val teamId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val shiftTypeIds: Set<UUID>,
    val createdAt: Instant,
    val createdBy: String,
    val revokedAt: Instant? = null,
    val revokedBy: String? = null,
) {
    val revoked: Boolean get() = revokedAt != null

    fun covers(nurseId: UUID, teamId: UUID, date: LocalDate, shiftTypeId: UUID): Boolean =
        !revoked && this.nurseId == nurseId && this.teamId == teamId &&
            date in startDate..endDate && (shiftTypeIds.isEmpty() || shiftTypeId in shiftTypeIds)
}
