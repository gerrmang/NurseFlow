package com.nurseflow.roster.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
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
) {
    @Transactional(readOnly = true)
    fun list(wardId: UUID, includeRevoked: Boolean, actor: String): List<SupportAllowance> {
        wards.requireAdmin(wardId, actor)
        return allowances.list(wardId, includeRevoked)
    }

    @Transactional
    fun create(wardId: UUID, input: SupportAllowanceInput, actor: String): SupportAllowance {
        wards.lockAsAdmin(wardId, actor)
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
        allowances.insert(allowance)
        audit.record(wardId, actor, "SUPPORT_ALLOWANCE_CREATED", allowance.id.toString())
        return allowance
    }

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
        const val MAX_SHIFT_TYPES = 20
    }
}
