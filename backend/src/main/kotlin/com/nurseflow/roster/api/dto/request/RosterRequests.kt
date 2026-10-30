package com.nurseflow.roster.api.dto.request

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateRosterRequest(val month: String)

data class StaffingRequirement(
    val date: LocalDate,
    val shiftTypeId: UUID,
    val requiredCount: Int,
    val requiredQualifications: List<String>,
)

data class ReplaceRequirementsRequest(val items: List<StaffingRequirement>)

data class ApprovedAbsenceInput(
    val nurseId: UUID,
    val kind: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class ValidateAssignmentRequest(val slotId: UUID, val nurseId: UUID)

data class AssignNurseRequest(val nurseId: UUID, val locked: Boolean = false)

data class ConfirmRosterRequest(val acknowledgeUnfilledCount: Int? = null)

data class StartGenerationRequest(
    val preserveLockedAssignments: Boolean,
    val timeLimitSeconds: Int = 120,
)
