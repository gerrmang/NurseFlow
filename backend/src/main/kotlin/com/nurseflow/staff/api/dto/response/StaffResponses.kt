package com.nurseflow.staff.api.dto.response

import java.time.Instant
import java.util.UUID

data class TeamResponse(val teamId: UUID, val name: String, val sortOrder: Int)

data class TeamItems(val items: List<TeamResponse>)

data class CareerPolicyResponse(val stageThresholdMonths: List<Int>, val ranks: List<String>, val isDefault: Boolean)

data class CareerProfileResponse(
    val status: String,
    val hospitalJoinedOn: String?,
    val careerStartedOn: String?,
    val rank: String?,
    val submittedAt: Instant?,
    val confirmedCareerStartedOn: String?,
    val confirmedRank: String?,
    val confirmedAt: Instant?,
    val experienceMonths: Int?,
    val submittedStage: Int?,
    val scoringStage: Int?,
)

data class StaffMemberResponse(
    val nurseId: UUID,
    val displayName: String,
    val qualifications: List<String>,
    val active: Boolean,
    val linkedAccount: Boolean,
    val team: TeamResponse?,
    val profile: CareerProfileResponse,
    val teamOrder: Int?,
    val seniorEligible: Boolean,
    val stageOverride: Int?,
    val rosterStage: Int?,
)

/** The nurse's own record: no team position, senior eligibility or stage override (admin judgements). */
data class MyStaffProfileResponse(
    val nurseId: UUID,
    val displayName: String,
    val qualifications: List<String>,
    val active: Boolean,
    val linkedAccount: Boolean,
    val team: TeamResponse?,
    val profile: CareerProfileResponse,
)

data class StaffMemberItems(val items: List<StaffMemberResponse>)

data class StaffAdjustmentResponse(
    val adjustmentId: UUID,
    val kind: String,
    val before: String?,
    val after: String?,
    val actorUserId: String,
    val actorName: String?,
    val at: Instant,
)

data class StaffAdjustmentItems(val items: List<StaffAdjustmentResponse>)

data class StaffPage(val items: List<StaffMemberResponse>, val page: Int, val pageSize: Int, val totalItems: Int)
