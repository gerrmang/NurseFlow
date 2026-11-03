package com.nurseflow.invitation.domain

import com.nurseflow.invitation.persistence.entity.DbInvitation
import java.time.Instant

enum class InvitationStatus { PENDING, REDEEMED, REVOKED, EXPIRED }

/** Redeemed and revoked are final; an unused invitation becomes EXPIRED at its deadline. */
fun DbInvitation.statusAt(now: Instant): InvitationStatus = when {
    redeemedAt != null -> InvitationStatus.REDEEMED
    revokedAt != null -> InvitationStatus.REVOKED
    !now.isBefore(expiresAt) -> InvitationStatus.EXPIRED
    else -> InvitationStatus.PENDING
}
