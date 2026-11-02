package com.nurseflow.staff.api.dto.response

import java.time.Instant
import java.util.UUID

data class TeamResponse(val teamId: UUID, val name: String, val sortOrder: Int)

data class TeamItems(val items: List<TeamResponse>)

data class CareerPolicyResponse(val stageThresholdMonths: List<Int>, val ranks: List<String>, val isDefault: Boolean)

data class CareerProfileResponse(
    val status: String,
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
)

data class StaffPage(val items: List<StaffMemberResponse>, val page: Int, val pageSize: Int, val totalItems: Int)
