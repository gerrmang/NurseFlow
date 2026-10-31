package com.nurseflow.roster.persistence.entity

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class DbRoster(
    val id: UUID,
    val wardId: UUID,
    val month: String,
    val status: String,
    val revision: Int,
    val zone: ZoneId,
    val publishedAt: Instant?,
    val confirmedBy: String?,
)
