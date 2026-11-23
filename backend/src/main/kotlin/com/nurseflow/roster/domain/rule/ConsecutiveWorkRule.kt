package com.nurseflow.roster.domain.rule

import com.nurseflow.roster.domain.model.RosterRestDay
import com.nurseflow.roster.domain.model.RuleSeverity
import com.nurseflow.roster.domain.model.RuleViolation
import com.nurseflow.roster.domain.model.ShiftAssignment
import java.time.LocalDate
import java.util.NavigableSet
import java.util.TreeSet
import java.util.UUID

/** Policy and known days for the current draft and the connected run of active published months. */
class ConsecutiveWorkRule(
    private val maxWorkDays: Int,
    private val coveredDays: ClosedRange<LocalDate>,
    restDays: Collection<RosterRestDay>,
) {
    private val restsByNurse: Map<UUID, NavigableSet<LocalDate>> = restDays.filter { it.date in coveredDays }.groupBy { it.nurseId }
        .mapValues { (_, rests) -> rests.mapTo(TreeSet()) { it.date } }

    fun validate(candidate: ShiftAssignment, assignments: Collection<ShiftAssignment>): RuleViolation? {
        val date = candidate.shiftDate
        require(date in coveredDays)
        val rests = restsByNurse[candidate.nurseId]
        if (rests?.contains(date) == true) return RuleViolation(
            "REST_DAY_ASSIGNMENT_CONFLICT", RuleSeverity.HARD, "휴식일에는 근무를 배정할 수 없습니다.", date,
        )
        val block = restBoundaries(date, rests)
        val days = mutableSetOf(date)
        for (other in assignments) {
            if (other.nurseId == candidate.nurseId && other.shiftDate in block) {
                days += other.shiftDate
                if (days.size > maxWorkDays) return limitViolation(date)
            }
        }
        return null
    }

    /**
     * A newly published month can connect two previously disjoint published runs even for a
     * nurse with no shift in this draft. Only blocks spanning this month can be newly affected.
     */
    fun bridgedViolation(month: ClosedRange<LocalDate>, assignments: Collection<ShiftAssignment>): RuleViolation? {
        val neighboring = assignments.asSequence()
            .filter { it.shiftDate in coveredDays && it.shiftDate !in month }
            .groupBy { it.nurseId }
        for ((nurse, shifts) in neighboring) {
            val rests = restsByNurse[nurse]
            if (rests?.ceiling(month.start)?.let { it <= month.endInclusive } == true) continue
            val block = restBoundaries(month.start, rests)
            val dates = shifts.asSequence().map { it.shiftDate }.filter { it in block }.toSet()
            if (dates.size > maxWorkDays && dates.any { it < month.start } && dates.any { it > month.endInclusive }) {
                return limitViolation(month.start)
            }
        }
        return null
    }

    private fun restBoundaries(date: LocalDate, rests: NavigableSet<LocalDate>?): ClosedRange<LocalDate> {
        val start = rests?.lower(date)?.plusDays(1) ?: coveredDays.start
        val end = rests?.higher(date)?.minusDays(1) ?: coveredDays.endInclusive
        return start..end
    }

    private fun limitViolation(date: LocalDate) = RuleViolation(
        "CONSECUTIVE_WORK_LIMIT", RuleSeverity.HARD, "휴식일 사이에 근무할 수 있는 최대 일수는 ${maxWorkDays}일입니다.", date,
    )
}
