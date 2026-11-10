package com.nurseflow.staff.persistence.entity

import java.time.Instant
import java.util.UUID

data class DbTeam(val id: UUID, val wardId: UUID, val name: String, val sortOrder: Int, val active: Boolean)

data class DbStaff(
    val id: UUID,
    val wardId: UUID,
    val subject: String?,
    val displayName: String,
    val qualifications: List<String>,
    val active: Boolean,
    val teamId: UUID?,
    val hospitalJoinedOn: String?,
    val careerStartedOn: String?,
    val rank: String?,
    val submittedAt: Instant?,
    val confirmedCareerStartedOn: String?,
    val confirmedRank: String?,
    val confirmedAt: Instant?,
    val teamOrder: Int?,
    val seniorEligible: Boolean,
    val stageOverride: Int?,
)

data class DbStaffAdjustment(
    val id: UUID,
    val kind: String,
    val before: String?,
    val after: String?,
    val actor: String,
    val actorName: String?,
    val at: Instant,
)
