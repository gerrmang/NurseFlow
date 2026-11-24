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

data class ConsecutiveWorkPolicy(val maxConsecutiveWorkDays: Int, val policyRevision: Int)

@Service
class ConsecutiveWorkPolicyService(
    private val wards: WardService,
    private val roster: RosterJdbcRepository,
    private val policy: ShiftTypeJdbcRepository,
) {
    @Transactional(readOnly = true)
    fun get(wardId: UUID, actor: String): ConsecutiveWorkPolicy {
        wards.requireAdmin(wardId, actor)
        // Reading revision first can at most yield a stale ETag, never old data with a newer ETag.
        val revision = policy.policyRevision(wardId)
        return ConsecutiveWorkPolicy(limit(wardId), revision)
    }

    @Transactional
    fun replace(wardId: UUID, maxDays: Int, ifMatch: String, actor: String): ConsecutiveWorkPolicy {
        wards.lockAsAdmin(wardId, actor)
        if (maxDays !in 1..31) throw BusinessException(ErrorCode.INVALID_REQUEST)
        val revision = policy.policyRevision(wardId)
        if (ifMatch != ShiftTypeService.policyEtag(revision)) throw BusinessException(ErrorCode.STALE_POLICY_REVISION)
        if (limit(wardId) == maxDays) return ConsecutiveWorkPolicy(maxDays, revision)
        roster.setMaxConsecutiveWorkDays(wardId, maxDays)
        policy.bumpPolicyRevision(wardId, actor, Instant.now())
        roster.invalidateWardDrafts(wardId)
        return ConsecutiveWorkPolicy(maxDays, policy.policyRevision(wardId))
    }

    private fun limit(wardId: UUID): Int =
        roster.maxConsecutiveWorkDays(wardId) ?: throw BusinessException(ErrorCode.WARD_NOT_FOUND)
}
