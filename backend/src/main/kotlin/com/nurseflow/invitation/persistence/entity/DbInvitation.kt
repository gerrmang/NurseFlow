package com.nurseflow.invitation.persistence.entity

import java.time.Instant
import java.util.UUID

data class DbInvitation(
    val id: UUID,
    val wardId: UUID,
    val role: String,
    val nurseId: UUID?,
    val createdAt: Instant,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val redeemedAt: Instant?,
)

data class DbInvitableNurse(val id: UUID, val active: Boolean, val linked: Boolean)
