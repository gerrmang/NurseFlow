package com.nurseflow.roster.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.idempotency.IdempotencyKeyRaceException
import com.nurseflow.common.idempotency.MutationReplayRepository
import com.nurseflow.common.idempotency.ReplayLookup
import com.nurseflow.roster.domain.model.SupportAllowance
import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import com.nurseflow.roster.persistence.repository.SupportAllowanceJdbcRepository
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.UUID

data class SupportAllowanceInput(
    val nurseId: UUID,
    val teamId: UUID,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val shiftTypeIds: List<UUID>,
)

/** [created] is false when an active allowance with the same scope already existed and is returned instead. */
data class SupportAllowanceGrant(val allowance: SupportAllowance, val created: Boolean)

/** Revoking an allowance flags the drafts that used it; published rosters are never changed. */
data class SupportAllowanceRevocation(val allowance: SupportAllowance, val flaggedDrafts: Int)

/**
 * Admin-granted cross-team support (A14). Writes take the ward lock, the same lock every roster mutation and
 * generation holds, so an assignment is checked either before or after a grant or revocation, never in between.
 */
@Service
class SupportAllowanceService(
    private val wards: WardService,
    private val roster: RosterJdbcRepository,
    private val allowances: SupportAllowanceJdbcRepository,
    private val audit: AuditLog,
    private val replays: MutationReplayRepository,
) {
    @Transactional(readOnly = true)
    fun list(wardId: UUID, includeRevoked: Boolean, actor: String): List<SupportAllowance> {
        wards.requireAdmin(wardId, actor)
        return allowances.list(wardId, includeRevoked)
    }

    @Transactional
    fun create(wardId: UUID, input: SupportAllowanceInput, idempotencyKey: UUID, actor: String): SupportAllowanceGrant {
        wards.lockAsAdmin(wardId, actor)
        // Read after the ward lock: a retry of a request whose response was lost replays that request, and returns
        // the record as it is now. A revocation in between stays in force; only a new key grants the scope again.
        val requestHash = replays.requestHash(listOf(actor, wardId, input))
        when (val saved = replays.replay(idempotencyKey, CREATE_OPERATION, requestHash, CreateReplay::class.java)) {
            ReplayLookup.Missing -> Unit
            ReplayLookup.Conflict -> throw BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED)
            is ReplayLookup.Found -> return SupportAllowanceGrant(
                allowances.find(wardId, saved.value.allowanceId)!!, saved.value.created,
            )
        }
        if (input.endDate < input.startDate || ChronoUnit.DAYS.between(input.startDate, input.endDate) >= MAX_DAYS ||
            input.shiftTypeIds.size > MAX_SHIFT_TYPES) {
            throw BusinessException(ErrorCode.INVALID_SUPPORT_ALLOWANCE)
        }
        val nurse = roster.findNurse(input.nurseId, wardId) ?: throw BusinessException(ErrorCode.NURSE_NOT_FOUND)
        if (input.teamId !in roster.activeTeamIds(wardId)) throw BusinessException(ErrorCode.TEAM_NOT_FOUND)
        if (nurse.teamId == input.teamId) {
            throw BusinessException(ErrorCode.INVALID_SUPPORT_ALLOWANCE, safeDetail = "간호사의 소속 팀에는 지원 허용이 필요하지 않습니다.")
        }
        val shiftTypeIds = input.shiftTypeIds.toSet()
        if (shiftTypeIds.any { roster.findShiftDefinition(it, wardId) == null }) {
            throw BusinessException(ErrorCode.SHIFT_TYPE_NOT_FOUND)
        }
        val allowance = SupportAllowance(
            UUID.randomUUID(), wardId, input.nurseId, input.teamId, input.startDate, input.endDate,
            shiftTypeIds, Instant.now(), actor,
        )
        // A second grant of an active scope returns that record instead of a twin that would outlive its revocation.
        val grant = allowances.findActiveSame(allowance)?.let { SupportAllowanceGrant(it, created = false) }
            ?: SupportAllowanceGrant(allowance, created = true).also {
                allowances.insert(allowance)
                audit.record(wardId, actor, "SUPPORT_ALLOWANCE_CREATED", allowance.id.toString())
            }
        try {
            replays.recordReplay(idempotencyKey, CREATE_OPERATION, requestHash, CreateReplay(grant.allowance.id, grant.created))
        } catch (_: IdempotencyKeyRaceException) {
            // The same key committed for another ward first; this ward was locked, so it is not this request.
            throw BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED)
        }
        return grant
    }

    /** What a replay needs: the record is re-read, so a retry shows a revocation made after the first request. */
    data class CreateReplay(val allowanceId: UUID, val created: Boolean)

    /** Idempotent: revoking a revoked allowance returns it unchanged and flags nothing. */
    @Transactional
    fun revoke(wardId: UUID, allowanceId: UUID, actor: String): SupportAllowanceRevocation {
        wards.lockAsAdmin(wardId, actor)
        val current = allowances.find(wardId, allowanceId) ?: throw BusinessException(ErrorCode.SUPPORT_ALLOWANCE_NOT_FOUND)
        if (current.revoked) return SupportAllowanceRevocation(current, 0)
        allowances.revoke(allowanceId, Instant.now(), actor)
        val flagged = allowances.markAffectedDrafts(current)
        audit.record(wardId, actor, "SUPPORT_ALLOWANCE_REVOKED", allowanceId.toString())
        return SupportAllowanceRevocation(allowances.find(wardId, allowanceId)!!, flagged)
    }

    companion object {
        const val MAX_DAYS = 366L
        const val CREATE_OPERATION = "create-support-allowance"
        const val MAX_SHIFT_TYPES = 20
    }
}
