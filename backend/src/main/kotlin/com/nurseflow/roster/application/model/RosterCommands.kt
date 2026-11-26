package com.nurseflow.roster.application.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateRosterRequest(val month: String)

data class StaffingRequirement(
    val date: LocalDate,
    val shiftTypeId: UUID,
    val requiredCount: Int,
    val requiredQualifications: List<String>,
    val teamId: UUID? = null,
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

/**
 * A16: the admin echoes each diagnostic the server showed. Null or empty means "not acknowledged"; the server
 * compares every list with its own calculation at confirm time and rejects any difference.
 */
data class ConfirmRosterRequest(val acknowledgements: ConfirmAcknowledgements? = null)

data class ConfirmAcknowledgements(
    val generalUnfilledSlotIds: List<UUID>? = null,
    val seniorUnfilledSlotIds: List<UUID>? = null,
    val hourShortfalls: List<HourShortfallAcknowledgement>? = null,
)

data class HourShortfallAcknowledgement(val nurseId: UUID, val shortfallMinutes: Int)

data class StartGenerationRequest(
    val preserveLockedAssignments: Boolean,
    val timeLimitSeconds: Int = 120,
)
