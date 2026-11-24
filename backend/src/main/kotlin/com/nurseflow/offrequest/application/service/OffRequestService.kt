package com.nurseflow.offrequest.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.offrequest.domain.OffRequest
import com.nurseflow.offrequest.domain.OffRequestPolicy
import com.nurseflow.offrequest.domain.OffRequestSource
import com.nurseflow.offrequest.domain.OffRequestStatus
import com.nurseflow.offrequest.persistence.repository.OffRequestJdbcRepository
import com.nurseflow.policy.application.service.ShiftTypeService
import com.nurseflow.policy.persistence.repository.ShiftTypeJdbcRepository
import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

data class OffRequestMonth(val items: List<OffRequest>, val deadline: Instant, val policy: OffRequestPolicy)

data class OffRequestPolicySnapshot(val policy: OffRequestPolicy, val policyRevision: Int)

/**
 * Preferred days off (A12). A nurse submits their own; a ward admin may enter one for any rosterable nurse of the
 * ward and is the only one who confirms or rejects. Before the month's deadline a nurse may submit (within the
 * monthly limit) and withdraw an undecided request. After it, a nurse can no longer withdraw; a new submission is
 * kept as an exception request marked `afterDeadline` that only takes effect once an admin confirms it. A nurse
 * cannot pick a past date. Admin entries are not limited by the deadline or the monthly limit and do not use up
 * the nurse's monthly count.
 *
 * Every write takes the ward lock first, so status, limit and duplicate checks see committed state. Only confirming,
 * or withdrawing a confirmed request, changes hard validation, so only those mark the month's drafts for
 * revalidation; published rosters stay as they are until re-confirmed.
 */
@Service
class OffRequestService(
    private val wards: WardService,
    private val requests: OffRequestJdbcRepository,
    private val roster: RosterJdbcRepository,
    private val policyRevisions: ShiftTypeJdbcRepository,
) {
    @Transactional(readOnly = true)
    fun list(wardId: UUID, month: YearMonth, actor: String): OffRequestMonth {
        val roles = roster.memberships(wardId, actor)
        if (roles.isEmpty()) throw BusinessException(ErrorCode.WARD_NOT_FOUND)
        val items = if (ADMIN in roles) requests.list(wardId, month) else requests.list(wardId, month, ownNurse(wardId, actor))
        val policy = policy(wardId)
        return OffRequestMonth(items, deadline(wardId, month, policy), policy)
    }

    /** [nurseId] is required from an admin and must be omitted, or be the caller's own record, from a nurse. */
    @Transactional
    fun submit(wardId: UUID, nurseId: UUID?, date: LocalDate, actor: String): OffRequest {
        wards.lockAsMember(wardId, actor)
        val admin = ADMIN in roster.memberships(wardId, actor)
        val target = if (admin) {
            nurseId ?: throw BusinessException(ErrorCode.INVALID_REQUEST, safeDetail = "대신 입력할 간호사를 선택해 주세요.")
        } else {
            ownNurse(wardId, actor).also { if (nurseId != null && nurseId != it) throw BusinessException(ErrorCode.WARD_ADMIN_REQUIRED) }
        }
        val zone = requests.wardZone(wardId) ?: throw BusinessException(ErrorCode.WARD_NOT_FOUND)
        if (!admin && date.isBefore(LocalDate.now(zone))) {
            throw BusinessException(ErrorCode.INVALID_REQUEST, safeDetail = "지난 날짜에는 희망 오프를 신청할 수 없습니다.")
        }
        if (roster.findNurse(target, wardId)?.active != true) throw BusinessException(ErrorCode.NURSE_NOT_FOUND)
        if (requests.holdsDate(target, date)) throw BusinessException(ErrorCode.OFF_REQUEST_DUPLICATE)
        val month = YearMonth.from(date)
        val policy = policy(wardId)
        val now = Instant.now()
        // Late exception requests still count: they are the nurse's own entries, kept until an admin decides them.
        if (!admin && requests.openNurseEntries(target, month) >= policy.monthlyLimit) {
            throw BusinessException(ErrorCode.OFF_REQUEST_LIMIT_REACHED, safeDetail = "희망 오프는 한 달에 ${policy.monthlyLimit}일까지 신청할 수 있습니다.")
        }
        val request = OffRequest(
            id = UUID.randomUUID(), wardId = wardId, nurseId = target, date = date,
            status = OffRequestStatus.SUBMITTED, source = if (admin) OffRequestSource.ADMIN else OffRequestSource.NURSE,
            submittedBy = actor, submittedAt = now, decidedBy = null, decidedAt = null,
            afterDeadline = !now.isBefore(policy.deadline(month, zone)),
        )
        requests.insert(request)
        return request
    }

    @Transactional
    fun confirm(wardId: UUID, requestId: UUID, actor: String): OffRequest =
        decideAsAdmin(wardId, requestId, actor, OffRequestStatus.CONFIRMED)

    @Transactional
    fun reject(wardId: UUID, requestId: UUID, actor: String): OffRequest =
        decideAsAdmin(wardId, requestId, actor, OffRequestStatus.REJECTED)

    /** A nurse withdraws only their own undecided request before the deadline; an admin also a confirmed one. */
    @Transactional
    fun withdraw(wardId: UUID, requestId: UUID, actor: String): OffRequest {
        wards.lockAsMember(wardId, actor)
        val admin = ADMIN in roster.memberships(wardId, actor)
        val current = requests.find(wardId, requestId) ?: throw BusinessException(ErrorCode.OFF_REQUEST_NOT_FOUND)
        if (!admin) {
            if (current.nurseId != ownNurse(wardId, actor)) throw BusinessException(ErrorCode.OFF_REQUEST_NOT_FOUND)
            if (current.status != OffRequestStatus.SUBMITTED) throw BusinessException(ErrorCode.OFF_REQUEST_STATE_CONFLICT)
            val month = YearMonth.from(current.date)
            if (!Instant.now().isBefore(deadline(wardId, month, policy(wardId)))) {
                throw BusinessException(ErrorCode.OFF_REQUEST_DEADLINE_PASSED)
            }
        } else if (!current.status.holdsDate) {
            throw BusinessException(ErrorCode.OFF_REQUEST_STATE_CONFLICT)
        }
        return transition(current, OffRequestStatus.WITHDRAWN, actor)
    }

    @Transactional(readOnly = true)
    fun getPolicy(wardId: UUID, actor: String): OffRequestPolicySnapshot {
        wards.requireAdmin(wardId, actor)
        // Reading revision first can at most yield a stale ETag, never old data with a newer ETag.
        val revision = policyRevisions.policyRevision(wardId)
        return OffRequestPolicySnapshot(policy(wardId), revision)
    }

    /** The settings only gate new nurse entries, so changing them leaves drafts and existing requests as they are. */
    @Transactional
    fun replacePolicy(wardId: UUID, monthlyLimit: Int, deadlineDay: Int, ifMatch: String, actor: String): OffRequestPolicySnapshot {
        wards.lockAsAdmin(wardId, actor)
        if (monthlyLimit !in OffRequestPolicy.LIMIT_RANGE || deadlineDay !in OffRequestPolicy.DEADLINE_DAY_RANGE) {
            throw BusinessException(ErrorCode.INVALID_REQUEST)
        }
        val revision = policyRevisions.policyRevision(wardId)
        if (ifMatch != ShiftTypeService.policyEtag(revision)) throw BusinessException(ErrorCode.STALE_POLICY_REVISION)
        val next = OffRequestPolicy(monthlyLimit, deadlineDay)
        if (policy(wardId) == next) return OffRequestPolicySnapshot(next, revision)
        requests.setPolicy(wardId, next)
        policyRevisions.bumpPolicyRevision(wardId, actor, Instant.now())
        return OffRequestPolicySnapshot(next, policyRevisions.policyRevision(wardId))
    }

    private fun decideAsAdmin(wardId: UUID, requestId: UUID, actor: String, next: OffRequestStatus): OffRequest {
        wards.lockAsAdmin(wardId, actor)
        val current = requests.find(wardId, requestId) ?: throw BusinessException(ErrorCode.OFF_REQUEST_NOT_FOUND)
        if (current.status != OffRequestStatus.SUBMITTED) throw BusinessException(ErrorCode.OFF_REQUEST_STATE_CONFLICT)
        return transition(current, next, actor)
    }

    private fun transition(current: OffRequest, next: OffRequestStatus, actor: String): OffRequest {
        val now = Instant.now()
        requests.decide(current.id, next, actor, now)
        if (current.status == OffRequestStatus.CONFIRMED || next == OffRequestStatus.CONFIRMED) {
            requests.invalidateMonthDrafts(current.wardId, YearMonth.from(current.date))
        }
        return current.copy(status = next, decidedBy = actor, decidedAt = now)
    }

    private fun policy(wardId: UUID): OffRequestPolicy =
        requests.policy(wardId) ?: throw BusinessException(ErrorCode.WARD_NOT_FOUND)

    private fun deadline(wardId: UUID, month: YearMonth, policy: OffRequestPolicy): Instant =
        policy.deadline(month, requests.wardZone(wardId) ?: throw BusinessException(ErrorCode.WARD_NOT_FOUND))

    /** The caller's own rosterable nurse record; an account without one (e.g. an admin) has no own requests. */
    private fun ownNurse(wardId: UUID, actor: String): UUID =
        roster.findNurseIdBySubject(wardId, actor) ?: throw BusinessException(
            ErrorCode.NURSE_IDENTITY_REQUIRED, safeDetail = "연결된 간호사 기록이 없어 희망 오프를 이용할 수 없습니다.",
        )

    private companion object {
        const val ADMIN = "WARD_ADMIN"
    }
}
