package com.nurseflow.staff.application.model

import com.nurseflow.staff.domain.ProfileStatus
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class TeamResult(val teamId: UUID, val name: String, val sortOrder: Int)

data class CareerPolicyResult(val stageThresholdMonths: List<Int>, val ranks: List<String>, val isDefault: Boolean)

data class ProfileInput(val careerStartedOn: YearMonth, val rank: String)

data class CareerProfileResult(
    val status: ProfileStatus,
    val hospitalJoinedOn: YearMonth?,
    val careerStartedOn: YearMonth?,
    val rank: String?,
    val submittedAt: Instant?,
    val confirmedCareerStartedOn: YearMonth?,
    val confirmedRank: String?,
    val confirmedAt: Instant?,
    /** Months of experience from the submitted start month, for display. */
    val experienceMonths: Int?,
    /** Stage from the submitted values; shown to admins before confirmation, never used for scoring. */
    val submittedStage: Int?,
    /**
     * Stage used for team career-mix scoring. Only set while status is CONFIRMED: a profile
     * changed after confirmation is excluded until the admin confirms it again.
     */
    val scoringStage: Int?,
)

data class StaffResult(
    val nurseId: UUID,
    val displayName: String,
    val qualifications: List<String>,
    val active: Boolean,
    val linkedAccount: Boolean,
    val team: TeamResult?,
    val profile: CareerProfileResult,
    /** Position inside the team, 1 = most senior; the admin's order wins over hire and career dates. */
    val teamOrder: Int?,
    val seniorEligible: Boolean,
    val stageOverride: Int?,
    /** Stage used for rostering: the admin override, else the confirmed computed stage. */
    val rosterStage: Int?,
)

data class RosterSettingsInput(val seniorEligible: Boolean, val stageOverride: Int?)

data class StaffAdjustmentResult(
    val adjustmentId: UUID,
    val kind: String,
    val before: String?,
    val after: String?,
    val actorUserId: String,
    val actorName: String?,
    val at: Instant,
)

data class StaffInputCommand(val displayName: String, val qualifications: List<String>, val active: Boolean, val teamId: UUID?)
