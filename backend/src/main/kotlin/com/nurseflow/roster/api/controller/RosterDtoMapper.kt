package com.nurseflow.roster.api.controller

import com.nurseflow.roster.api.dto.request.*
import com.nurseflow.roster.api.dto.response.*
import com.nurseflow.roster.application.model.CreateRosterRequest as CreateCommand
import com.nurseflow.roster.application.model.ReplaceRequirementsRequest as RequirementsCommand
import com.nurseflow.roster.application.model.ApprovedAbsenceInput as AbsenceCommand
import com.nurseflow.roster.application.model.ValidateAssignmentRequest as ValidateCommand
import com.nurseflow.roster.application.model.AssignNurseRequest as AssignCommand
import com.nurseflow.roster.application.model.ConfirmRosterRequest as ConfirmCommand
import com.nurseflow.roster.application.model.StartGenerationRequest as GenerationCommand

internal fun CreateRosterRequest.toCommand() = CreateCommand(month)
internal fun ReplaceRequirementsRequest.toCommand() = RequirementsCommand(items.map {
    com.nurseflow.roster.application.model.StaffingRequirement(it.date, it.shiftTypeId, it.requiredCount, it.requiredQualifications)
})
internal fun ApprovedAbsenceInput.toCommand() = AbsenceCommand(nurseId, kind, startsAt, endsAt)
internal fun ValidateAssignmentRequest.toCommand() = ValidateCommand(slotId, nurseId)
internal fun AssignNurseRequest.toCommand() = AssignCommand(nurseId, locked)
internal fun ConfirmRosterRequest.toCommand() = ConfirmCommand(acknowledgeUnfilledCount)
internal fun StartGenerationRequest.toCommand() = GenerationCommand(preserveLockedAssignments, timeLimitSeconds)

internal fun com.nurseflow.roster.application.model.StaffingSummary.toResponse() =
    StaffingSummary(required, assigned, unfilled, hardRuleViolations)
internal fun com.nurseflow.roster.application.model.AssignmentView.toResponse() =
    AssignmentView(nurseId, displayName, locked)
internal fun com.nurseflow.roster.application.model.SlotView.toResponse() =
    SlotView(slotId, date, shiftTypeId, shiftCode, shiftKind, startAt, endAt, status, assignment?.toResponse())
internal fun com.nurseflow.roster.application.model.RosterSummary.toResponse() =
    RosterSummary(rosterId, wardId, month, status, revision, timeZone, staffing.toResponse(), publishedAt, needsRevalidation)
internal fun com.nurseflow.roster.application.model.RosterView.toResponse() =
    RosterView(rosterId, wardId, month, status, revision, timeZone, staffing.toResponse(), slots.map { it.toResponse() }, publishedAt, needsRevalidation)
internal fun com.nurseflow.roster.application.model.ApprovedAbsenceView.toResponse() =
    ApprovedAbsenceView(absenceId, nurseId, kind, startsAt, endsAt)
internal fun com.nurseflow.roster.application.model.RuleDiagnostic.toResponse() =
    RuleDiagnostic(code, severity, message, affectedDate)
internal fun com.nurseflow.roster.application.model.AssignmentValidation.toResponse() =
    AssignmentValidation(allowed, hardViolations.map { it.toResponse() }, softWarnings.map { it.toResponse() }, rosterRevision)
internal fun com.nurseflow.roster.application.model.GenerationJob.toResponse() =
    GenerationJob(jobId, rosterId, status, sourceRosterRevision, createdAt, progressPercent,
        resultRosterRevision, staffing?.toResponse(), diagnostics.map { it.toResponse() }, finishedAt)
internal fun com.nurseflow.roster.application.model.PublishedWardCalendar.toResponse() =
    PublishedWardCalendar(wardId, month, revision, timeZone, staffing.toResponse(), slots.map { it.toResponse() })
internal fun com.nurseflow.roster.application.model.PersonalAssignment.toResponse() =
    PersonalAssignment(date, shiftType, startsAt, endsAt)
internal fun com.nurseflow.roster.application.model.PublishedPersonalCalendar.toResponse() =
    PublishedPersonalCalendar(wardId, month, revision, timeZone, assignments.map { it.toResponse() })
