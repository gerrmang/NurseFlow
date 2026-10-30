package com.nurseflow.roster.api.dto.response

import com.nurseflow.roster.domain.model.RuleViolation
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class StaffingSummary(val required: Int, val assigned: Int, val unfilled: Int, val hardRuleViolations: Int = 0)

data class AssignmentView(val nurseId: UUID, val displayName: String, val locked: Boolean)

data class SlotView(
    val slotId: UUID,
    val date: LocalDate,
    val shiftTypeId: UUID,
    val startAt: Instant,
    val endAt: Instant,
    val status: String,
    val assignment: AssignmentView?,
)

data class RosterSummary(
    val rosterId: UUID,
    val wardId: UUID,
    val month: String,
    val status: String,
    val revision: Int,
    val timeZone: String,
    val staffing: StaffingSummary,
    val publishedAt: Instant? = null,
)

data class RosterView(
    val rosterId: UUID,
    val wardId: UUID,
    val month: String,
    val status: String,
    val revision: Int,
    val timeZone: String,
    val staffing: StaffingSummary,
    val slots: List<SlotView>,
    val publishedAt: Instant? = null,
)

data class ApprovedAbsenceView(
    val absenceId: UUID,
    val nurseId: UUID,
    val kind: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class AbsenceMutation(val absence: ApprovedAbsenceView, val rosterRevision: Int)

data class RuleDiagnostic(
    val code: String,
    val severity: String,
    val message: String,
    val affectedDate: LocalDate? = null,
) {
    companion object {
        fun from(violation: RuleViolation) = RuleDiagnostic(
            violation.code, violation.severity.name, violation.message, violation.affectedDate,
        )
    }
}

data class AssignmentValidation(
    val allowed: Boolean,
    val hardViolations: List<RuleDiagnostic>,
    val softWarnings: List<RuleDiagnostic> = emptyList(),
    val rosterRevision: Int,
)

data class GenerationJob(
    val jobId: UUID,
    val rosterId: UUID,
    val status: String,
    val sourceRosterRevision: Int,
    val createdAt: Instant,
    val progressPercent: Int? = null,
    val resultRosterRevision: Int? = null,
    val staffing: StaffingSummary? = null,
    val diagnostics: List<RuleDiagnostic> = emptyList(),
    val finishedAt: Instant? = null,
)

data class PublishedWardCalendar(
    val wardId: UUID,
    val month: String,
    val revision: Int,
    val timeZone: String,
    val staffing: StaffingSummary,
    val slots: List<SlotView>,
)

data class PersonalAssignment(
    val date: LocalDate,
    val shiftType: String,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class PublishedPersonalCalendar(
    val wardId: UUID,
    val month: String,
    val revision: Int,
    val timeZone: String,
    val assignments: List<PersonalAssignment>,
)

data class Items<T>(val items: List<T>)
