package com.nurseflow.roster.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * shiftDate is the calendar date in the ward's configured timezone. The exact
 * ward timezone is supplied when shift instances are created; this validator
 * never guesses it from the server timezone.
 */
data class ShiftAssignment(
    val assignmentId: UUID?,
    val nurseId: UUID,
    val shiftDate: LocalDate,
    val kind: ShiftKind,
    val startsAt: Instant,
    val endsAt: Instant,
)
