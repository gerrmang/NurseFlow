package com.nurseflow.roster.persistence.entity

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class NewSlot(
    val date: LocalDate,
    val shiftTypeId: UUID,
    val startsAt: Instant,
    val endsAt: Instant,
    val requiredQualifications: List<String>,
)
