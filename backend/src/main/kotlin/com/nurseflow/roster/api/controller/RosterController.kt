package com.nurseflow.roster.api.controller

import com.nurseflow.common.error.ApiException
import com.nurseflow.roster.api.dto.request.*
import com.nurseflow.roster.api.dto.response.*
import com.nurseflow.roster.application.generation.GenerationCoordinator
import com.nurseflow.roster.application.service.RosterService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1")
class RosterController(private val service: RosterService, private val generation: GenerationCoordinator) {
    private fun subject(jwt: Jwt): String = jwt.subject
        ?: throw ApiException(HttpStatus.UNAUTHORIZED, "SUBJECT_REQUIRED", "사용자 식별 정보가 없습니다.")

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
