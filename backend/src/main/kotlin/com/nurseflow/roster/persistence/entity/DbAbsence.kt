package com.nurseflow.roster.persistence.entity

import java.time.Instant
import java.util.UUID

data class DbAbsence(
    val id: UUID,
    val nurseId: UUID,
    val kind: String,
    val startsAt: Instant,
    val endsAt: Instant,
)
