package com.nurseflow.roster.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.policy.application.service.ShiftTypeService
import com.nurseflow.policy.persistence.repository.ShiftTypeJdbcRepository
import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

data class EligibilityItem(val nurseId: UUID, val displayName: String, val eligible: Boolean, val reason: String)
data class EligibilityResult(val demoMode: Boolean, val policyRevision: Int, val items: List<EligibilityItem>)

@Service
class RosterEligibilityService(
    private val wards: WardService,
    private val roster: RosterJdbcRepository,
    private val policy: ShiftTypeJdbcRepository,
) {
    @Transactional(readOnly = true)
    fun get(wardId: UUID, actor: String): EligibilityResult {
        wards.requireAdmin(wardId, actor)
        return snapshot(wardId)
    }

    @Transactional
    fun setDemoMode(wardId: UUID, enabled: Boolean, ifMatch: String, actor: String): EligibilityResult {
        wards.lockAsAdmin(wardId, actor)
        val revision = policy.policyRevision(wardId)
        if (ifMatch != ShiftTypeService.policyEtag(revision)) {
            throw BusinessException(ErrorCode.STALE_POLICY_REVISION)
        }
        val current = roster.demoMode(wardId) ?: throw BusinessException(ErrorCode.WARD_NOT_FOUND)
        if (current != enabled) {
            roster.setDemoMode(wardId, enabled)
            policy.bumpPolicyRevision(wardId, actor, Instant.now())
            roster.invalidateDraftsForEligibility(wardId)
        }
        return snapshot(wardId)
    }

    private fun snapshot(wardId: UUID): EligibilityResult {
        // Read revision first: a concurrent policy update may make the ETag stale (412 on write),
        // but must never return old eligibility data with a newer ETag.
        val revision = policy.policyRevision(wardId)
        val demoMode = roster.demoMode(wardId) ?: throw BusinessException(ErrorCode.WARD_NOT_FOUND)
        val items = roster.eligibilityRows(wardId).map { row ->
            val reason = when {
                !row.staffActive -> "STAFF_INACTIVE"
                row.subject == null && !demoMode -> "DEMO_MODE_DISABLED"
                row.adminActive -> "ADMIN_EXCLUDED"
                row.subject != null && !row.memberActive -> "MEMBERSHIP_INACTIVE_OR_MISSING"
                else -> "ELIGIBLE"
            }
            EligibilityItem(row.id, row.displayName, row.eligible, reason)
        }
        return EligibilityResult(demoMode, revision, items)
    }
}
