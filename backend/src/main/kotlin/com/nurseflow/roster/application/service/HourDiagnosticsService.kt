package com.nurseflow.roster.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.policy.application.service.TargetHourPolicyService
import com.nurseflow.policy.domain.HourNurse
import com.nurseflow.policy.domain.MonthTarget
import com.nurseflow.policy.domain.NurseHours
import com.nurseflow.policy.domain.TargetHourCalculator
import com.nurseflow.policy.domain.TargetHourPolicy
import com.nurseflow.policy.persistence.repository.ShiftTypeJdbcRepository
import com.nurseflow.policy.persistence.repository.TargetHourJdbcRepository
import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth
import java.util.UUID

data class HourDiagnostics(
    val rosterId: UUID,
    val month: YearMonth,
    val status: String,
    val revision: Int,
    val policyRevision: Int,
    val policy: TargetHourPolicy,
    val target: MonthTarget,
    /** False when the previous month has no published roster, so its month-end Night cannot carry in. */
    val previousMonthPublished: Boolean,
    val nurses: List<NurseHours>,
)

/**
 * Per-nurse actual, credited, target and remaining minutes of one roster (A13). Admin only: it shows every
 * nurse's hours, and drafts are never shown to nurses. Computed on read from the roster and the ward's current
 * policy, so it never goes stale and changes nothing.
 */
@Service
class HourDiagnosticsService(
    private val rosters: RosterJdbcRepository,
    private val hours: TargetHourJdbcRepository,
    private val policies: TargetHourPolicyService,
    private val revisions: ShiftTypeJdbcRepository,
    private val wards: WardService,
) {
    @Transactional(readOnly = true)
    fun diagnose(rosterId: UUID, actor: String): HourDiagnostics {
        val roster = rosters.findRoster(rosterId) ?: throw BusinessException(ErrorCode.ROSTER_NOT_FOUND)
        wards.requireAdmin(roster.wardId, actor)
        val month = YearMonth.parse(roster.month)
        val policyRevision = revisions.policyRevision(roster.wardId)
        val policy = policies.policyOf(roster.wardId)
        val target = policies.monthTarget(roster.wardId, month, policy)
        // Decision 14: the previous month's last-day Night ends in this month and counts here. Only a published
        // previous month is authoritative; its draft may still change.
        val previous = rosters.activePublished(roster.wardId, month.minusMonths(1).toString())
        val carried = previous?.let { prev -> hours.assignedShifts(prev.id).filter { TargetHourCalculator.attributedMonth(it, roster.zone) == month } }
            .orEmpty()
        val shifts = hours.assignedShifts(rosterId) + carried
        // Leave stored on a published neighbouring month can run into this month; the calculator clips it to the month.
        val neighbours = listOf(month.minusMonths(1), month.plusMonths(1))
            .mapNotNull { rosters.activePublished(roster.wardId, it.toString()) }.filter { it.id != rosterId }
        val monthStart = month.atDay(1).atStartOfDay(roster.zone).toInstant()
        val monthEnd = month.plusMonths(1).atDay(1).atStartOfDay(roster.zone).toInstant()
        val absences = hours.absences(rosterId) +
            neighbours.flatMap { hours.absences(it.id) }.filter { it.startsAt < monthEnd && it.endsAt > monthStart }
        val restDays = hours.restDays(rosterId)
        val rosterable = rosters.activeNurses(roster.wardId).map { it.id }.toSet()
        val involved = rosterable + shifts.map { it.nurseId } + absences.map { it.nurseId } + restDays.map { it.nurseId }
        // Inactive or non-rosterable records appear only when this roster still holds time for them.
        val nurses = hours.wardNurses(roster.wardId).filter { it.nurseId in involved }
            .map { HourNurse(it.nurseId, it.displayName, it.workPattern, it.nurseId in rosterable) }
        return HourDiagnostics(
            roster.id, month, roster.status, roster.revision, policyRevision, policy, target, previous != null,
            TargetHourCalculator.diagnose(month, roster.zone, target, policy, nurses, shifts, absences, restDays),
        )
    }
}
