package com.nurseflow.invitation.api.dto.request

import com.nurseflow.common.security.OpaqueToken
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
)

/** Only the token: role and ward are taken from the stored invitation. */
data class InvitationTokenRequest(
    @field:Pattern(regexp = OpaqueToken.PATTERN)
    val token: String,
)

/**
 * Joining a ward. WARD_MEMBER invitees may submit their career start (YYYY-MM).
 * Submitted values still need admin confirmation. Ignored for WARD_ADMIN.
 */
data class RedeemInvitationRequest(
    @field:Pattern(regexp = OpaqueToken.PATTERN)
    val token: String,
    @field:Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$")
    val careerStartedOn: String? = null,
)
