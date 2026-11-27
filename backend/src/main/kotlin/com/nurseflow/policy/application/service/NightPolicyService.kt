package com.nurseflow.policy.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.policy.domain.NightPolicy
import com.nurseflow.policy.persistence.repository.NightPolicyJdbcRepository
import com.nurseflow.policy.persistence.repository.ShiftTypeJdbcRepository
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class NightPolicyResult(val policy: NightPolicy, val policyRevision: Int)

/**
 * Ward SOF and Night-dedicated settings (A15), admin only. Writes share the ward policy revision and its If-Match
 * check with the other ward settings. Like target hours they do not flag drafts: SOF need and dedicated Nights are
 * diagnostics computed on read, not placement rules.
 */
@Service
class NightPolicyService(
    private val repo: NightPolicyJdbcRepository,
    private val revisions: ShiftTypeJdbcRepository,
    private val wards: WardService,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun get(wardId: UUID, actor: String): NightPolicyResult {
        wards.requireAdmin(wardId, actor)
        val revision = revisions.policyRevision(wardId)
        return NightPolicyResult(policyOf(wardId), revision)
    }

    /** [request] is read only after the admin check, so a non-admin gets 403/404 whatever the body holds. */
    @Transactional
    fun replace(wardId: UUID, request: () -> NightPolicy, ifMatch: String, actor: String): NightPolicyResult {
        wards.lockAsAdmin(wardId, actor)
        val policy = request()
        if (!policy.isValid()) throw BusinessException(ErrorCode.INVALID_NIGHT_POLICY)
        val current = revisions.policyRevision(wardId)
        if (ifMatch != ShiftTypeService.policyEtag(current)) {
            throw BusinessException(ErrorCode.STALE_POLICY_REVISION, currentRevision = current)
        }
        if (policyOf(wardId) == policy) return NightPolicyResult(policy, current)
        val now = Instant.now()
        repo.save(wardId, policy, actor, now)
        revisions.bumpPolicyRevision(wardId, actor, now)
        audit.record(wardId, actor, "NIGHT_POLICY_UPDATED", wardId.toString())
        return NightPolicyResult(policy, revisions.policyRevision(wardId))
    }

    fun policyOf(wardId: UUID): NightPolicy = repo.policy(wardId) ?: NightPolicy()
}
