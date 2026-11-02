package com.nurseflow.roster.api.dto.request

import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.error.InvalidRequestCode
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/*
 * Bean Validation mirrors the OpenAPI request constraints at the HTTP boundary.
 * The services keep their own checks because they are also called without HTTP
 * (tests, generation jobs). @InvalidRequestCode preserves the public error codes
 * that these inputs produced before validation moved to the controller boundary.
 */

@InvalidRequestCode(ErrorCode.INVALID_MONTH)
data class CreateRosterRequest(
    @field:Pattern(regexp = MONTH_PATTERN)
    val month: String,
)

data class StaffingRequirement(
    val date: LocalDate,
    val shiftTypeId: UUID,
    @field:Min(0) @field:Max(100)
    val requiredCount: Int,
    @field:Size(max = 50)
    val requiredQualifications: List<@Size(min = 1, max = 64) String>,
)

@InvalidRequestCode(ErrorCode.INVALID_REQUIREMENTS)
data class ReplaceRequirementsRequest(
    @field:Size(max = 1000) @field:Valid
    val items: List<StaffingRequirement>,
)

@InvalidRequestCode(ErrorCode.INVALID_ABSENCE)
data class ApprovedAbsenceInput(
    val nurseId: UUID,
    @field:Pattern(regexp = "APPROVED_LEAVE|APPROVED_HALF_DAY")
    val kind: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class ValidateAssignmentRequest(val slotId: UUID, val nurseId: UUID)

data class AssignNurseRequest(val nurseId: UUID, val locked: Boolean = false)

data class ConfirmRosterRequest(
    @field:PositiveOrZero
    val acknowledgeUnfilledCount: Int? = null,
)

@InvalidRequestCode(ErrorCode.INVALID_TIME_LIMIT)
data class StartGenerationRequest(
    val preserveLockedAssignments: Boolean,
    @field:Min(1) @field:Max(600)
    val timeLimitSeconds: Int = 120,
)

const val MONTH_PATTERN = "^\\d{4}-(0[1-9]|1[0-2])$"
