package com.nurseflow.invitation.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.security.OpaqueToken
import com.nurseflow.invitation.application.model.CreateInvitationCommand
import com.nurseflow.invitation.application.model.InvitationResult
import com.nurseflow.invitation.application.model.IssuedInvitation
import com.nurseflow.invitation.config.InvitationProperties
import com.nurseflow.invitation.domain.InvitationStatus
import com.nurseflow.invitation.domain.statusAt
import com.nurseflow.invitation.persistence.entity.DbInvitation
import com.nurseflow.invitation.persistence.repository.InvitationJdbcRepository
import com.nurseflow.ward.application.model.WardRole
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** Ward admins issue, list and revoke single-use, expiring invitation links. */
@Service
class InvitationService(
    private val repo: InvitationJdbcRepository,
    private val wards: WardService,
    private val audit: AuditLog,
    private val properties: InvitationProperties,
) {
    @Transactional
    fun create(wardId: UUID, command: CreateInvitationCommand, actor: String): IssuedInvitation {
        wards.lockAsAdmin(wardId, actor)
        if (command.role == WardRole.WARD_ADMIN && command.nurseId != null) {
            // Admin accounts are excluded from rostering, so they never take over a nurse record.
            throw BusinessException(ErrorCode.INVALID_INVITATION)
        }
        val now = Instant.now()
        command.nurseId?.let { requireInvitableNurse(wardId, it, now) }

        return issue(wardId, command.role, command.nurseId, actor, now, "INVITATION_CREATED")
    }

    /** Reissues an invitation with the same role and nurse target, revoking its old unused link. */
    @Transactional
    fun reissue(wardId: UUID, invitationId: UUID, actor: String): IssuedInvitation {
        // Ward first, then the invitation row: the same order as create and redemption, so they cannot deadlock.
        wards.lockAsAdmin(wardId, actor)
        val previous = repo.lockInWard(wardId, invitationId)
            ?: throw BusinessException(ErrorCode.INVITATION_NOT_FOUND)
        val now = Instant.now()
        when (previous.statusAt(now)) {
            InvitationStatus.REDEEMED -> throw BusinessException(ErrorCode.INVITATION_ALREADY_REDEEMED)
            InvitationStatus.PENDING -> {
                repo.revoke(previous.id, now)
                audit.record(wardId, actor, "INVITATION_REVOKED", previous.id.toString())
            }
            InvitationStatus.REVOKED -> throw BusinessException(ErrorCode.INVITATION_REVOKED)
            InvitationStatus.EXPIRED -> {
                repo.revoke(previous.id, now)
                audit.record(wardId, actor, "INVITATION_REVOKED", previous.id.toString())
            }
        }
        previous.nurseId?.let { requireInvitableNurse(wardId, it, now) }
        audit.record(wardId, actor, "INVITATION_REISSUED", previous.id.toString())
        return issue(wardId, WardRole.valueOf(previous.role), previous.nurseId, actor, now, "INVITATION_CREATED")
    }

    private fun issue(
        wardId: UUID,
        role: WardRole,
        nurseId: UUID?,
        actor: String,
        now: Instant,
        auditAction: String,
    ): IssuedInvitation {

        val token = OpaqueToken.generate()
        val id = UUID.randomUUID()
        val expiresAt = now.plus(Duration.ofHours(24))
        repo.insert(id, wardId, role.name, nurseId, OpaqueToken.sha256Hex(token), actor, now, expiresAt)
        audit.record(wardId, actor, auditAction, id.toString())
        val result = InvitationResult(
            id, wardId, role, nurseId, InvitationStatus.PENDING, now, expiresAt, null, null,
        )
        return IssuedInvitation(result, token, inviteUrl(token))
    }

    @Transactional(readOnly = true)
    fun list(wardId: UUID, actor: String): List<InvitationResult> {
        wards.requireAdmin(wardId, actor)
        val now = Instant.now()
        return repo.listByWard(wardId).map { it.toResult(now) }
    }

    /** Idempotent for already revoked invitations; a redeemed invitation cannot be revoked. */
    @Transactional
    fun revoke(wardId: UUID, invitationId: UUID, actor: String) {
        wards.lockAsAdmin(wardId, actor)
        val invitation = repo.lockInWard(wardId, invitationId)
            ?: throw BusinessException(ErrorCode.INVITATION_NOT_FOUND)
        when (invitation.statusAt(Instant.now())) {
            InvitationStatus.REDEEMED -> throw BusinessException(ErrorCode.INVITATION_ALREADY_REDEEMED)
            InvitationStatus.REVOKED -> return
            InvitationStatus.PENDING, InvitationStatus.EXPIRED -> {
                repo.revoke(invitationId, Instant.now())
                audit.record(wardId, actor, "INVITATION_REVOKED", invitationId.toString())
            }
        }
    }

    private fun requireInvitableNurse(wardId: UUID, nurseId: UUID, now: Instant) {
        val nurse = repo.findNurseInWard(wardId, nurseId)
        if (nurse == null || !nurse.active) throw BusinessException(ErrorCode.NURSE_NOT_FOUND)
        if (nurse.linked) throw BusinessException(ErrorCode.NURSE_ALREADY_LINKED)
        if (repo.openInvitationsForNurse(nurseId).any { it.statusAt(now) == InvitationStatus.PENDING }) {
            throw BusinessException(ErrorCode.INVITATION_ALREADY_PENDING)
        }
    }

    private fun inviteUrl(token: String): String? =
        properties.linkBaseUrl.trim().takeIf { it.isNotEmpty() }?.let { "$it#token=$token" }

    private fun DbInvitation.toResult(now: Instant) = InvitationResult(
        id, wardId, WardRole.valueOf(role), nurseId, statusAt(now), createdAt, expiresAt, redeemedAt, revokedAt,
    )
}
