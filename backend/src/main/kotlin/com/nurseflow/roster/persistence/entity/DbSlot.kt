package com.nurseflow.roster.persistence.entity

import com.nurseflow.roster.domain.model.ShiftAssignment
import com.nurseflow.roster.domain.model.ShiftKind
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class DbSlot(
    val id: UUID,
    val rosterId: UUID,
    val date: LocalDate,
    val shiftTypeId: UUID,
    val shiftCode: String,
    val kind: ShiftKind,
    val startsAt: Instant,
    val endsAt: Instant,
    val requiredQualifications: Set<String>,
    val nurseId: UUID?,
    val nurseName: String?,
    val locked: Boolean,
) {
    fun assignment(): ShiftAssignment? = nurseId?.let {
        ShiftAssignment(id, it, date, kind, startsAt, endsAt)
    }
}
