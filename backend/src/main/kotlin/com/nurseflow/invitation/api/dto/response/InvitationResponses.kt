package com.nurseflow.invitation.api.dto.response

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant
import java.util.UUID

@JsonInclude(JsonInclude.Include.NON_NULL)
data class InvitationResponse(
    val invitationId: UUID,
    val wardId: UUID,
    val role: String,
    val nurseId: UUID?,
    val status: String,
    val createdAt: Instant,
    val expiresAt: Instant,
    val redeemedAt: Instant?,
    val revokedAt: Instant?,
)

/** Creation response: `token` is shown once and cannot be retrieved again. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CreatedInvitationResponse(
    val invitation: InvitationResponse,
    val token: String,
    val inviteUrl: String?,
)

data class InvitationItems(val items: List<InvitationResponse>)
