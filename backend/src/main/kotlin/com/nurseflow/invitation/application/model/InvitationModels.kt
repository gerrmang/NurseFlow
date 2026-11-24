package com.nurseflow.invitation.application.model

import com.nurseflow.invitation.domain.InvitationStatus
import com.nurseflow.ward.application.model.WardRole
import java.time.Instant
import java.util.UUID

data class CreateInvitationCommand(val role: WardRole, val nurseId: UUID?)

data class InvitationResult(
    val invitationId: UUID,
    val wardId: UUID,
    val role: WardRole,
    val nurseId: UUID?,
    val status: InvitationStatus,
    val createdAt: Instant,
    val expiresAt: Instant,
    val redeemedAt: Instant?,
    val revokedAt: Instant?,
)

/** Returned once at creation. The raw token is never stored and cannot be retrieved again. */
data class IssuedInvitation(val invitation: InvitationResult, val token: String, val inviteUrl: String?)

data class InvitationPreview(
    val wardId: UUID,
    val wardName: String,
    val role: WardRole,
    val expiresAt: Instant,
    val linksExistingNurse: Boolean,
    /** Same ward names are allowed across hospitals; this tells the invitee which one they join. */
    val hospitalName: String?,
)
