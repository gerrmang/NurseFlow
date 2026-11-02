package com.nurseflow.invitation.api.dto.request

import com.nurseflow.common.security.OpaqueToken
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
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

/**
 * Joining a ward. WARD_MEMBER invitees may submit their career start (YYYY-MM) and rank in the
 * same call; both or neither. Submitted values still need admin confirmation. Ignored for WARD_ADMIN.
 */
data class RedeemInvitationRequest(
    @field:Pattern(regexp = OpaqueToken.PATTERN)
    val token: String,
    @field:Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$")
    val careerStartedOn: String? = null,
    @field:Size(min = 1, max = 30)
    val rank: String? = null,
) {
    @AssertTrue
    fun isProfileComplete(): Boolean = (careerStartedOn == null) == (rank == null)
}
