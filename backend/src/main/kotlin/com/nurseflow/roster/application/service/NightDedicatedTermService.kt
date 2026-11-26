package com.nurseflow.roster.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.policy.application.service.NightPolicyService
import com.nurseflow.policy.application.service.TargetHourPolicyService
import com.nurseflow.roster.domain.model.NightDedicatedTerm
import com.nurseflow.roster.domain.model.NightDedicatedTermStatus
import com.nurseflow.roster.persistence.repository.NightDedicatedTermJdbcRepository
import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

/** [created] is false when the same live term already existed and is returned instead. */
data class NightDedicatedTermGrant(val term: NightDedicatedTerm, val created: Boolean)

/**
 * Night-dedicated rotation records (A15), admin only. A term names who is dedicated in which months; it changes no
 * assignment and no hard rule, so writes flag no drafts. Night diagnostics read the terms on every request.
 */
@Service
class NightDedicatedTermService(
    private val wards: WardService,
    private val roster: RosterJdbcRepository,
    private val terms: NightDedicatedTermJdbcRepository,
    private val policies: NightPolicyService,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun list(wardId: UUID, includeCancelled: Boolean, actor: String): List<NightDedicatedTerm> {
        wards.requireAdmin(wardId, actor)
        return terms.list(wardId).filter { includeCancelled || it.status != NightDedicatedTermStatus.CANCELLED }
    }

    /**
     * Records a term from [startMonth] for the ward's rotation length, or up to [endMonth] when given. The same live
     * term again returns the stored one (a retried request makes no twin); any other overlap for the nurse is a 409.
     */
    @Transactional
    fun create(wardId: UUID, nurseId: UUID, startMonth: String, endMonth: String?, actor: String): NightDedicatedTermGrant {
        wards.lockAsAdmin(wardId, actor)
        val start = TargetHourPolicyService.parseMonth(startMonth)
        val end = endMonth?.let(TargetHourPolicyService::parseMonth)
            ?: start.plusMonths(policies.policyOf(wardId).dedicatedRotationMonths - 1L)
        if (end < start || monthsBetween(start, end) >= MAX_TERM_MONTHS) {
            throw BusinessException(ErrorCode.INVALID_NIGHT_DEDICATED_TERM)
        }
        val nurse = roster.findNurse(nurseId, wardId) ?: throw BusinessException(ErrorCode.NURSE_NOT_FOUND)
        if (!nurse.active) {
            throw BusinessException(ErrorCode.INVALID_NIGHT_DEDICATED_TERM, safeDetail = "편성 대상 간호사만 Night 전담으로 지정할 수 있습니다.")
        }
        val term = NightDedicatedTerm(UUID.randomUUID(), wardId, nurseId, start, end, end, Instant.now(), actor)
        val existing = terms.forNurse(wardId, nurseId)
        existing.firstOrNull { it.status == NightDedicatedTermStatus.ACTIVE && it.startMonth == start && it.endMonth == end }
            ?.let { return NightDedicatedTermGrant(it, created = false) }
        if (existing.any { it.overlaps(term) }) throw BusinessException(ErrorCode.NIGHT_DEDICATED_TERM_OVERLAP)
        terms.insert(term)
        audit.record(wardId, actor, "NIGHT_DEDICATED_TERM_CREATED", term.id.toString())
        return NightDedicatedTermGrant(term, created = true)
    }

    /**
     * Ends a term after [lastMonth], which may only move the end earlier. The month before the start cancels the
     * term. Repeating the current end returns the term unchanged. The row is kept as history either way.
     */
    @Transactional
    fun end(wardId: UUID, termId: UUID, lastMonth: String, actor: String): NightDedicatedTerm {
        wards.lockAsAdmin(wardId, actor)
        val last = TargetHourPolicyService.parseMonth(lastMonth)
        val term = terms.find(wardId, termId) ?: throw BusinessException(ErrorCode.NIGHT_DEDICATED_TERM_NOT_FOUND)
        if (last == term.endMonth) return term
        if (last > term.endMonth || last < term.startMonth.minusMonths(1)) {
            throw BusinessException(ErrorCode.INVALID_NIGHT_DEDICATED_TERM)
        }
        terms.end(termId, last, Instant.now(), actor)
        audit.record(wardId, actor, "NIGHT_DEDICATED_TERM_ENDED", termId.toString())
        return terms.find(wardId, termId)!!
    }

    companion object {
        const val MAX_TERM_MONTHS = 12

        private fun monthsBetween(from: YearMonth, to: YearMonth): Long = java.time.temporal.ChronoUnit.MONTHS.between(from, to)
    }
}
