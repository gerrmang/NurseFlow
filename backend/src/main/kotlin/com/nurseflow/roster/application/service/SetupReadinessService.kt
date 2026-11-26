package com.nurseflow.roster.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.policy.persistence.repository.TargetHourJdbcRepository
import com.nurseflow.roster.application.model.RuleDiagnostic
import com.nurseflow.roster.domain.readiness.ReadinessItem
import com.nurseflow.roster.domain.readiness.ReadinessNurse
import com.nurseflow.roster.domain.readiness.ReadinessSlot
import com.nurseflow.roster.domain.readiness.RosterSetupInput
import com.nurseflow.roster.domain.readiness.SeniorCoverage
import com.nurseflow.roster.domain.readiness.SetupReadinessEvaluator
import com.nurseflow.roster.domain.readiness.WardSetupInput
import com.nurseflow.roster.persistence.entity.DbRoster
import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import com.nurseflow.roster.persistence.repository.SetupReadinessJdbcRepository
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth
import java.util.UUID

data class WardSetupReadiness(val wardId: UUID, val ready: Boolean, val items: List<ReadinessItem>)

data class GenerationPrecheck(
    val rosterId: UUID,
    val wardId: UUID,
    val month: YearMonth,
    val revision: Int,
    val ready: Boolean,
    val items: List<ReadinessItem>,
    val maxShiftsPerNurse: Int,
    val seniorCoverage: List<SeniorCoverage>,
)

/**
 * Setup readiness of a ward and pre-generation check of a draft (A18). Admin only, computed on read and changes
 * nothing. Missing required settings block [RosterService.startGeneration]; everything else is a notice.
 */
@Service
class SetupReadinessService(
    private val rosters: RosterJdbcRepository,
    private val readiness: SetupReadinessJdbcRepository,
    private val hours: TargetHourJdbcRepository,
    private val wards: WardService,
) {
    @Transactional(readOnly = true)
    fun ward(wardId: UUID, actor: String): WardSetupReadiness {
        wards.requireAdmin(wardId, actor)
        val items = SetupReadinessEvaluator.ward(wardInput(wardId))
        return WardSetupReadiness(wardId, items.none { it.blocking }, items)
    }

    @Transactional(readOnly = true)
    fun precheck(rosterId: UUID, actor: String): GenerationPrecheck {
        val roster = rosters.findRoster(rosterId) ?: throw BusinessException(ErrorCode.ROSTER_NOT_FOUND)
        wards.requireAdmin(roster.wardId, actor)
        if (roster.status != "DRAFT") throw BusinessException(ErrorCode.NOT_DRAFT)
        return evaluate(roster)
    }

    /** One HARD SETUP_<key> diagnostic per missing required item; empty when generation may run. */
    fun blockingDiagnostics(roster: DbRoster): List<RuleDiagnostic> =
        evaluate(roster).items.filter { it.blocking }.map { RuleDiagnostic("SETUP_${it.key.name}", "HARD", it.message) }

    /** Throws SETUP_INCOMPLETE listing each missing required item. Caller has already authorized the admin. */
    fun requireReadyForGeneration(roster: DbRoster) {
        val blocking = blockingDiagnostics(roster)
        if (blocking.isNotEmpty()) {
            throw BusinessException(
                ErrorCode.SETUP_INCOMPLETE,
                ruleViolations = blocking,
                safeDetail = "필수 설정이 빠져 자동배치를 시작할 수 없습니다: ${blocking.joinToString(" / ") { it.message }}",
            )
        }
    }

    private fun evaluate(roster: DbRoster): GenerationPrecheck {
        val month = YearMonth.parse(roster.month)
        val ward = wardInput(roster.wardId)
        val rosterInput = RosterSetupInput(
            month,
            rosters.slots(roster.id).map { ReadinessSlot(it.date, it.kind, it.teamId, it.seniorRequired, it.startsAt, it.endsAt) },
            roster.needsRevalidation,
            hours.publicHolidayFetchedAt(month) != null || hours.calendar(roster.wardId, month).isNotEmpty(),
            readiness.pendingOffRequests(roster.wardId, month),
        )
        val items = SetupReadinessEvaluator.roster(ward, rosterInput)
        return GenerationPrecheck(
            roster.id, roster.wardId, month, roster.revision, items.none { it.blocking }, items,
            SetupReadinessEvaluator.maxShiftsPerNurse(month, ward.maxConsecutiveWorkDays),
            SetupReadinessEvaluator.seniorCoverage(ward, rosterInput),
        )
    }

    private fun wardInput(wardId: UUID): WardSetupInput {
        val rules = readiness.wardRules(wardId) ?: throw BusinessException(ErrorCode.WARD_NOT_FOUND)
        return WardSetupInput(
            readiness.activeShiftKinds(wardId),
            readiness.activeTeams(wardId),
            rosters.activeNurses(wardId).map { ReadinessNurse(it.id, it.teamId, it.seniorEligible, it.workPattern) },
            rules.maxConsecutiveWorkDays,
            rules.maxOffRequestsPerMonth,
            rules.offRequestDeadlineDay,
            hours.policy(wardId) != null,
        )
    }
}
