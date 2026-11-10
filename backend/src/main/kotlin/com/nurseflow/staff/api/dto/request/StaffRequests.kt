package com.nurseflow.staff.api.dto.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.error.InvalidRequestCode
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import java.util.UUID

const val YEAR_MONTH_PATTERN = "^\\d{4}-(0[1-9]|1[0-2])$"

data class TeamRequest(
    @field:NotBlank @field:Size(max = 50)
    val name: String,
    @field:Min(1) @field:Max(1000)
    val sortOrder: Int? = null,
)

@InvalidRequestCode(ErrorCode.INVALID_CAREER_POLICY)
data class CareerPolicyRequest(
    /** Lower bounds (months) of stages 2..5, strictly increasing. Default [12, 36, 72, 120]. */
    @field:Size(min = 4, max = 4)
    val stageThresholdMonths: List<@Min(1) @Max(600) Int>,
    @field:Size(min = 1, max = 20)
    val ranks: List<@NotBlank @Size(max = 30) String>,
)

/** Career start (입사 년월, YYYY-MM) and rank chosen from the ward's rank list. */
data class CareerProfileRequest(
    @field:Pattern(regexp = YEAR_MONTH_PATTERN)
    val careerStartedOn: String,
    @field:NotBlank @field:Size(max = 30)
    val rank: String,
)

/** Nurse-submitted month they joined this hospital; career history is administrator-managed. */
data class HospitalJoinMonthRequest(
    @field:Pattern(regexp = YEAR_MONTH_PATTERN)
    val hospitalJoinedOn: String,
)

data class StaffRequest(
    @field:NotBlank @field:Size(max = 100)
    val displayName: String,
    @field:Size(max = 50)
    val qualifications: List<@Size(min = 1, max = 64) String> = emptyList(),
    val active: Boolean = true,
    val teamId: UUID? = null,
)

data class TeamAssignmentRequest(val teamId: UUID?)

/** The team's active nurses, most senior first, as dragged by the admin. */
data class TeamOrderRequest(
    @field:Size(max = 200)
    val nurseIds: List<UUID>,
)

/**
 * Full replacement: both fields are required so a client that only toggles seniority cannot clear the
 * stage override by leaving it out. Send `stageOverride: null` to use the computed stage.
 */
data class RosterSettingsRequest(
    @param:JsonProperty(required = true)
    val seniorEligible: Boolean,
    @param:JsonProperty(required = true) @field:Min(1) @field:Max(5)
    val stageOverride: Int?,
)
