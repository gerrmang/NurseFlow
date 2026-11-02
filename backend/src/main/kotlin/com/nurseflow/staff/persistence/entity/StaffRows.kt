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
    val careerStartedOn: String?,
    val rank: String?,
    val submittedAt: Instant?,
    val confirmedCareerStartedOn: String?,
    val confirmedRank: String?,
    val confirmedAt: Instant?,
)
