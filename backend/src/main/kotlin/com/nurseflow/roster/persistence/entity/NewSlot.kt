package com.nurseflow.roster.persistence.entity

import com.nurseflow.roster.domain.model.ShiftKind
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class NewSlot(
    val date: LocalDate,
    val shiftTypeId: UUID,
    val shiftCode: String,
    val shiftKind: ShiftKind,
    val startsAt: Instant,
    val endsAt: Instant,
    val requiredQualifications: List<String>,
)
