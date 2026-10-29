package com.nurseflow.roster.api

import com.nurseflow.roster.application.RosterService
import com.nurseflow.roster.application.GenerationCoordinator
import com.nurseflow.roster.domain.RuleViolation
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class CreateRosterRequest(val month: String)
data class StaffingRequirement(
    val date: LocalDate,
    val shiftTypeId: UUID,
    val requiredCount: Int,
    val requiredQualifications: List<String>,
)
data class ReplaceRequirementsRequest(val items: List<StaffingRequirement>)
data class ApprovedAbsenceInput(
    val nurseId: UUID,
    val kind: String,
    val startsAt: Instant,
    val endsAt: Instant,
)
data class ValidateAssignmentRequest(val slotId: UUID, val nurseId: UUID)
data class AssignNurseRequest(val nurseId: UUID, val locked: Boolean = false)
data class ConfirmRosterRequest(val acknowledgeUnfilledCount: Int? = null)
data class StartGenerationRequest(
    val preserveLockedAssignments: Boolean,
    val timeLimitSeconds: Int = 120,
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

class RosterApiException(
    val status: HttpStatus,
    val code: String,
    val safeDetail: String,
    val currentRevision: Int? = null,
    val ruleViolations: List<RuleDiagnostic> = emptyList(),
) : RuntimeException(safeDetail)

data class ApiProblem(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val code: String,
    val detail: String,
    val currentRevision: Int? = null,
    val ruleViolations: List<RuleDiagnostic> = emptyList(),
)

@RestControllerAdvice
class RosterApiErrors {
    @ExceptionHandler(RosterApiException::class)
    fun handle(ex: RosterApiException): ResponseEntity<ApiProblem> = ResponseEntity
        .status(ex.status)
        .contentType(MediaType.parseMediaType("application/problem+json"))
        .header("Cache-Control", "private, no-store")
        .body(ApiProblem(title = ex.status.reasonPhrase, status = ex.status.value(), code = ex.code,
            detail = ex.safeDetail, currentRevision = ex.currentRevision, ruleViolations = ex.ruleViolations))

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingRequestHeaderException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun malformed(): ResponseEntity<ApiProblem> = ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .contentType(MediaType.parseMediaType("application/problem+json"))
        .header("Cache-Control", "private, no-store")
        .body(ApiProblem(title = "Bad Request", status = 400, code = "INVALID_REQUEST",
            detail = "요청 형식 또는 필드 값이 올바르지 않습니다."))
}

@RestController
@RequestMapping("/api/v1")
class RosterController(private val service: RosterService, private val generation: GenerationCoordinator) {
    private fun subject(jwt: Jwt): String = jwt.subject
        ?: throw RosterApiException(HttpStatus.UNAUTHORIZED, "SUBJECT_REQUIRED", "사용자 식별 정보가 없습니다.")

    @GetMapping("/wards/{wardId}/rosters")
    fun list(@PathVariable wardId: UUID, @RequestParam month: String, @AuthenticationPrincipal jwt: Jwt): Items<RosterSummary> =
        Items(service.listRosters(wardId, month, subject(jwt)))

    @PostMapping("/wards/{wardId}/rosters")
    fun create(
        @PathVariable wardId: UUID,
        @RequestBody request: CreateRosterRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<RosterView> {
        val result = service.createRoster(wardId, request, subject(jwt), idempotencyKey)
        return ResponseEntity.status(HttpStatus.CREATED).eTag(service.etag(result.rosterId, result.revision)).body(result)
    }

    @GetMapping("/rosters/{rosterId}")
    fun get(@PathVariable rosterId: UUID, @AuthenticationPrincipal jwt: Jwt): ResponseEntity<RosterView> {
        val result = service.getRoster(rosterId, subject(jwt))
        return ResponseEntity.ok().eTag(service.etag(result.rosterId, result.revision)).body(result)
    }

    @PutMapping("/rosters/{rosterId}/requirements")
    fun requirements(
        @PathVariable rosterId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @RequestBody request: ReplaceRequirementsRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<RosterSummary> {
        val result = service.replaceRequirements(rosterId, ifMatch, request, subject(jwt))
        return ResponseEntity.ok().eTag(service.etag(result.rosterId, result.revision)).body(result)
    }

    @GetMapping("/rosters/{rosterId}/absences")
    fun absences(@PathVariable rosterId: UUID, @AuthenticationPrincipal jwt: Jwt): Items<ApprovedAbsenceView> =
        Items(service.listAbsences(rosterId, subject(jwt)))

    @PostMapping("/rosters/{rosterId}/absences")
    fun addAbsence(
        @PathVariable rosterId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @RequestBody request: ApprovedAbsenceInput,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApprovedAbsenceView> {
        val result = service.addAbsence(rosterId, ifMatch, idempotencyKey, request, subject(jwt))
        return ResponseEntity.status(HttpStatus.CREATED).eTag(service.etag(rosterId, result.rosterRevision)).body(result.absence)
    }

    @PutMapping("/rosters/{rosterId}/absences/{absenceId}")
    fun updateAbsence(
        @PathVariable rosterId: UUID,
        @PathVariable absenceId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @RequestBody request: ApprovedAbsenceInput,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ApprovedAbsenceView> {
        val result = service.updateAbsence(rosterId, absenceId, ifMatch, request, subject(jwt))
        return ResponseEntity.ok().eTag(service.etag(rosterId, result.rosterRevision)).body(result.absence)
    }

    @DeleteMapping("/rosters/{rosterId}/absences/{absenceId}")
    fun deleteAbsence(
        @PathVariable rosterId: UUID,
        @PathVariable absenceId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        val revision = service.deleteAbsence(rosterId, absenceId, ifMatch, subject(jwt))
        return ResponseEntity.noContent().eTag(service.etag(rosterId, revision)).build()
    }

    @PostMapping("/rosters/{rosterId}/assignments/validate")
    fun validate(
        @PathVariable rosterId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @RequestBody request: ValidateAssignmentRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): AssignmentValidation = service.validate(rosterId, ifMatch, request, subject(jwt))

    @PutMapping("/rosters/{rosterId}/slots/{slotId}/assignment")
    fun assign(
        @PathVariable rosterId: UUID,
        @PathVariable slotId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @RequestBody request: AssignNurseRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<RosterView> {
        val result = service.assign(rosterId, slotId, ifMatch, idempotencyKey, request, subject(jwt))
        return ResponseEntity.ok().eTag(service.etag(result.rosterId, result.revision)).body(result)
    }

    @DeleteMapping("/rosters/{rosterId}/slots/{slotId}/assignment")
    fun unassign(
        @PathVariable rosterId: UUID,
        @PathVariable slotId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<Void> {
        val revision = service.unassign(rosterId, slotId, ifMatch, subject(jwt))
        return ResponseEntity.noContent().eTag(service.etag(rosterId, revision)).build()
    }

    @PostMapping("/rosters/{rosterId}/confirm")
    fun confirm(
        @PathVariable rosterId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @RequestBody request: ConfirmRosterRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<RosterSummary> {
        val result = service.confirm(rosterId, ifMatch, idempotencyKey, request, subject(jwt))
        return ResponseEntity.ok().eTag(service.etag(result.rosterId, result.revision)).body(result)
    }

    @PostMapping("/rosters/{rosterId}/generation-jobs")
    fun startGeneration(
        @PathVariable rosterId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @RequestBody request: StartGenerationRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<GenerationJob> = ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(generation.start(rosterId, ifMatch, idempotencyKey, request, subject(jwt)))

    @GetMapping("/generation-jobs/{jobId}")
    fun generationJob(@PathVariable jobId: UUID, @AuthenticationPrincipal jwt: Jwt): GenerationJob =
        service.getGenerationJob(jobId, subject(jwt))

    @PostMapping("/generation-jobs/{jobId}/cancel")
    fun cancelGeneration(
        @PathVariable jobId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<GenerationJob> = ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(service.cancelGeneration(jobId, idempotencyKey, subject(jwt)))

    @GetMapping("/wards/{wardId}/published-rosters")
    fun publishedWard(@PathVariable wardId: UUID, @RequestParam month: String, @AuthenticationPrincipal jwt: Jwt):
        ResponseEntity<PublishedWardCalendar> = ResponseEntity.ok()
            .header("Cache-Control", "private, no-store")
            .body(service.publishedWard(wardId, month, subject(jwt)))

    @GetMapping("/wards/{wardId}/published-rosters/mine")
    fun publishedMine(@PathVariable wardId: UUID, @RequestParam month: String, @AuthenticationPrincipal jwt: Jwt):
        ResponseEntity<PublishedPersonalCalendar> = ResponseEntity.ok()
            .header("Cache-Control", "private, no-store")
            .body(service.publishedMine(wardId, month, subject(jwt)))
}
