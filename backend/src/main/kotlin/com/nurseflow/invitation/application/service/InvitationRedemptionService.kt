package com.nurseflow.invitation.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.security.OpaqueToken
import com.nurseflow.invitation.application.model.InvitationPreview
import com.nurseflow.invitation.domain.InvitationStatus
import com.nurseflow.invitation.domain.statusAt
import com.nurseflow.invitation.persistence.entity.DbInvitation
import com.nurseflow.invitation.persistence.repository.InvitationJdbcRepository
import com.nurseflow.ward.application.model.WardMembershipResult
import com.nurseflow.ward.application.model.WardRole
import com.nurseflow.ward.persistence.repository.WardJdbcRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * A signed-in user turns an invitation into ward access. The role always comes from the
 * invitation; the request carries only the token, so users cannot choose or raise their role.
 */
@Service
class InvitationRedemptionService(
    private val repo: InvitationJdbcRepository,
    private val wardRepo: WardJdbcRepository,
    private val audit: AuditLog,
) {
    /** Lets the invitee confirm the ward and role before joining. Does not consume the invitation. */
    @Transactional(readOnly = true)
    fun preview(token: String): InvitationPreview {
        val invitation = repo.findByTokenHash(OpaqueToken.sha256Hex(token))
            ?: throw BusinessException(ErrorCode.INVITATION_NOT_FOUND)
        requireRedeemable(invitation, Instant.now())
        val wardName = wardRepo.findWardName(invitation.wardId)
            ?: throw BusinessException(ErrorCode.INVITATION_NOT_FOUND)
        return InvitationPreview(
            invitation.wardId, wardName, WardRole.valueOf(invitation.role), invitation.expiresAt, invitation.nurseId != null,
        )
    }

    /**
     * Consumes the invitation and creates the membership (and, for WARD_MEMBER, the nurse link)
     * in one transaction. Any failure rolls everything back and leaves the invitation usable.
     */
    @Transactional
    fun redeem(token: String, subject: String): WardMembershipResult {
        val now = Instant.now()
        val invitation = repo.lockByTokenHash(OpaqueToken.sha256Hex(token))
            ?: throw BusinessException(ErrorCode.INVITATION_NOT_FOUND)
        requireRedeemable(invitation, now)
        if (!wardRepo.lockWard(invitation.wardId)) throw BusinessException(ErrorCode.INVITATION_NOT_FOUND)
        if (wardRepo.rolesIn(invitation.wardId, subject).isNotEmpty()) {
            // Role changes for existing members are an admin action, not a second invitation.
            throw BusinessException(ErrorCode.ALREADY_WARD_MEMBER)
        }
        val role = WardRole.valueOf(invitation.role)
        if (role == WardRole.WARD_MEMBER) linkNurse(invitation, subject)
        wardRepo.insertMembership(invitation.wardId, subject, role.name, canViewWardCalendar = true)
        repo.markRedeemed(invitation.id, subject, now)
        audit.record(invitation.wardId, subject, "INVITATION_REDEEMED", invitation.id.toString())
        val wardName = wardRepo.findWardName(invitation.wardId)!!
        return WardMembershipResult(invitation.wardId, wardName, listOf(role), canViewWardCalendar = true)
    }

    private fun linkNurse(invitation: DbInvitation, subject: String) {
        if (repo.nurseLinkedTo(invitation.wardId, subject)) throw BusinessException(ErrorCode.ALREADY_WARD_MEMBER)
        val nurseId = invitation.nurseId
        if (nurseId != null) {
            if (!repo.linkNurse(invitation.wardId, nurseId, subject)) throw BusinessException(ErrorCode.NURSE_ALREADY_LINKED)
        } else {
            val name = repo.userDisplayName(subject) ?: throw BusinessException(ErrorCode.USER_NOT_FOUND)
            repo.insertNurse(UUID.randomUUID(), invitation.wardId, subject, name)
        }
    }

    private fun requireRedeemable(invitation: DbInvitation, now: Instant) {
        when (invitation.statusAt(now)) {
            InvitationStatus.PENDING -> Unit
            InvitationStatus.REDEEMED -> throw BusinessException(ErrorCode.INVITATION_ALREADY_REDEEMED)
            InvitationStatus.REVOKED -> throw BusinessException(ErrorCode.INVITATION_REVOKED)
            InvitationStatus.EXPIRED -> throw BusinessException(ErrorCode.INVITATION_EXPIRED)
        }
    }
}
