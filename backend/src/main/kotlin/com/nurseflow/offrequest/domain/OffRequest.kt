package com.nurseflow.offrequest.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** SUBMITTED is a preference only; CONFIRMED blocks assignment; REJECTED and WITHDRAWN are kept as history. */
enum class OffRequestStatus {
    SUBMITTED, CONFIRMED, REJECTED, WITHDRAWN;

    /** Holds the nurse's date: a second request for the same date is a duplicate while one of these exists. */
    val holdsDate: Boolean get() = this == SUBMITTED || this == CONFIRMED
}

/** Who entered the request: the nurse in the app, or a ward admin on the nurse's behalf. */
enum class OffRequestSource { NURSE, ADMIN }

data class OffRequest(
    val id: UUID,
    val wardId: UUID,
    val nurseId: UUID,
    val date: LocalDate,
    val status: OffRequestStatus,
    val source: OffRequestSource,
    val submittedBy: String,
    val submittedAt: Instant,
    val decidedBy: String?,
    val decidedAt: Instant?,
    val afterDeadline: Boolean,
)
