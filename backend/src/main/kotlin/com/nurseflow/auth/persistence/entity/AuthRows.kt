package com.nurseflow.auth.persistence.entity

import java.time.Instant
import java.util.UUID

data class DbUser(val id: UUID, val displayName: String)

data class DbRefreshToken(
    val id: UUID,
    val familyId: UUID,
    val userId: UUID,
    val expiresAt: Instant,
    val replacedAt: Instant?,
    val revokedAt: Instant?,
)
