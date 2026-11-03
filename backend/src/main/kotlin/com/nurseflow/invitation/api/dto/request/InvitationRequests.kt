package com.nurseflow.invitation.api.dto.request

import com.nurseflow.common.security.OpaqueToken
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import java.util.UUID

/**
 * WARD_MEMBER may optionally take over an existing (fictional) nurse record. WARD_ADMIN
 * invitations never link a nurse because administrators are excluded from rostering.
 */
data class CreateInvitationRequest(
    @field:Pattern(regexp = "WARD_ADMIN|WARD_MEMBER")
    val role: String,
    val nurseId: UUID? = null,
    @field:Min(1) @field:Max(168)
    val expiresInHours: Int = 72,
)

/** Only the token: role and ward are taken from the stored invitation. */
data class InvitationTokenRequest(
    @field:Pattern(regexp = OpaqueToken.PATTERN)
    val token: String,
)
