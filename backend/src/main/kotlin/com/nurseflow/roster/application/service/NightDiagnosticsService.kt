package com.nurseflow.roster.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.policy.application.service.NightPolicyService
import com.nurseflow.policy.domain.NightPolicy
import com.nurseflow.policy.persistence.repository.ShiftTypeJdbcRepository
import com.nurseflow.policy.persistence.repository.TargetHourJdbcRepository
import com.nurseflow.roster.domain.model.NightDiagnosticsCalculator
import com.nurseflow.roster.domain.model.NightMonthDiagnosis
import com.nurseflow.roster.domain.model.NightNurse
import com.nurseflow.roster.domain.model.RestDayKind
import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.persistence.repository.NightDedicatedTermJdbcRepository
import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth
import java.util.UUID

data class NightDiagnostics(
    val rosterId: UUID,
    val month: YearMonth,
    val status: String,
    val revision: Int,
    val policyRevision: Int,
    val policy: NightPolicy,
    val diagnosis: NightMonthDiagnosis,
)

/**
 * SOF need and Night-dedicated checks of one roster (A15). Admin only, like hour diagnostics: drafts are never shown
 * to nurses. Computed on read from the roster, the ward's Night policy and its dedicated terms; it changes nothing
 * and blocks nothing. SOF itself is placed by the admin through the roster's rest days (A11).
 */
@Service
class NightDiagnosticsService(
    private val rosters: RosterJdbcRepository,
    private val hours: TargetHourJdbcRepository,
    private val terms: NightDedicatedTermJdbcRepository,
    private val policies: NightPolicyService,
    private val revisions: ShiftTypeJdbcRepository,
    private val wards: WardService,
) {
    @Transactional(readOnly = true)
    fun diagnose(rosterId: UUID, actor: String): NightDiagnostics {
        val roster = rosters.findRoster(rosterId) ?: throw BusinessException(ErrorCode.ROSTER_NOT_FOUND)
        wards.requireAdmin(roster.wardId, actor)
        val month = YearMonth.parse(roster.month)
        val policyRevision = revisions.policyRevision(roster.wardId)
        val policy = policies.policyOf(roster.wardId)
        // A roster holds only slots dated in its month, so this counts Nights by the date they start.
        val nights = hours.assignedShifts(rosterId).filter { it.kind == ShiftKind.NIGHT }
            .groupingBy { it.nurseId }.eachCount()
        val sof = hours.restDays(rosterId).filter { it.kind == RestDayKind.SOF }.groupingBy { it.nurseId }.eachCount()
        val wardTerms = terms.list(roster.wardId)
        val rosterable = rosters.activeNurses(roster.wardId).map { it.id }.toSet()
        val covered = wardTerms.filter { it.covers(month) }.map { it.nurseId }
        val involved = rosterable + nights.keys + sof.keys + covered
        val inWard = hours.wardNurses(roster.wardId).filter { it.nurseId in involved }
        val movedAway = (involved - inWard.map { it.nurseId }.toSet()).mapNotNull(hours::nurse)
        val nurses = (inWard + movedAway).map { NightNurse(it.nurseId, it.displayName, it.workPattern, it.nurseId in rosterable) }
        return NightDiagnostics(
            roster.id, month, roster.status, roster.revision, policyRevision, policy,
            NightDiagnosticsCalculator.diagnose(month, policy, nurses, nights, sof, wardTerms),
        )
    }
}
