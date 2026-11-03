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
import com.nurseflow.ward.persistence.repository.WardJdbcRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/** Ward admins issue, list and revoke single-use, expiring invitation links. */
@Service
class InvitationService(
    private val repo: InvitationJdbcRepository,
    private val wardRepo: WardJdbcRepository,
    private val wards: WardService,
    private val audit: AuditLog,
    private val properties: InvitationProperties,
) {
    @Transactional
    fun create(wardId: UUID, command: CreateInvitationCommand, actor: String): IssuedInvitation {
        wards.requireAdmin(wardId, actor)
        if (!wardRepo.lockWard(wardId)) throw BusinessException(ErrorCode.WARD_NOT_FOUND)
        if (command.role == WardRole.WARD_ADMIN && command.nurseId != null) {
            // Admin accounts are excluded from rostering, so they never take over a nurse record.
            throw BusinessException(ErrorCode.INVALID_INVITATION)
        }
        val now = Instant.now()
        command.nurseId?.let { requireInvitableNurse(wardId, it, now) }

        val token = OpaqueToken.generate()
        val id = UUID.randomUUID()
        val expiresAt = now.plus(command.validFor)
        repo.insert(id, wardId, command.role.name, command.nurseId, OpaqueToken.sha256Hex(token), actor, now, expiresAt)
        audit.record(wardId, actor, "INVITATION_CREATED", id.toString())
        val result = InvitationResult(
            id, wardId, command.role, command.nurseId, InvitationStatus.PENDING, now, expiresAt, null, null,
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
        wards.requireAdmin(wardId, actor)
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
