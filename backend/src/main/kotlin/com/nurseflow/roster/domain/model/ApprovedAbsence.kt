package com.nurseflow.roster.domain.model

import java.time.Instant
import java.util.UUID

enum class ApprovedAbsenceKind {
    APPROVED_LEAVE,
    APPROVED_HALF_DAY,
}

/** Stores only the approved time interval and category, never a leave reason. */
data class ApprovedAbsence(
    val nurseId: UUID,
    val kind: ApprovedAbsenceKind,
    val startsAt: Instant,
    val endsAt: Instant,
)
