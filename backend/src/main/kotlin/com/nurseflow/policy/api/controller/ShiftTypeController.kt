package com.nurseflow.policy.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.policy.api.dto.request.ShiftTypeRequest
import com.nurseflow.policy.api.dto.response.AffectedDraftResponse
import com.nurseflow.policy.api.dto.response.ShiftTypeChangePreviewResponse
import com.nurseflow.policy.api.dto.response.ShiftTypeItems
import com.nurseflow.policy.api.dto.response.ShiftTypeResponse
import com.nurseflow.policy.application.model.ShiftTypeCommand
import com.nurseflow.policy.application.model.ShiftTypeResult
import com.nurseflow.policy.application.model.ShiftTypeWrite
import com.nurseflow.policy.application.service.ShiftTypeService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
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
import java.time.LocalTime
import java.util.UUID

/**
 * Ward shift types. Members read them; only admins change them. Every response carries the ward policy revision as
 * an ETag, and PUT/DELETE require it in If-Match so concurrent admin edits cannot overwrite each other.
 */
@RestController
@RequestMapping("/api/v1/wards/{wardId}/shift-types")
class ShiftTypeController(private val shiftTypes: ShiftTypeService) {
    @GetMapping
    fun list(
        @PathVariable wardId: UUID,
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
        @CurrentSubject subject: String,
    ): ResponseEntity<ShiftTypeItems> = shiftTypes.list(wardId, includeInactive, subject).let { result ->
        ResponseEntity.ok().eTag(ShiftTypeService.policyEtag(result.policyRevision))
            .body(ShiftTypeItems(result.items.map { it.toResponse() }, result.policyRevision))
    }

    @PostMapping
    fun create(
        @PathVariable wardId: UUID,
        @Valid @RequestBody request: ShiftTypeRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @CurrentSubject subject: String,
    ): ResponseEntity<ShiftTypeResponse> =
        shiftTypes.create(wardId, request.toCommand(), idempotencyKey, subject).toEntity(HttpStatus.CREATED)

    @PutMapping("/{shiftTypeId}")
    fun update(
        @PathVariable wardId: UUID,
        @PathVariable shiftTypeId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @Valid @RequestBody request: ShiftTypeRequest,
        @CurrentSubject subject: String,
    ): ResponseEntity<ShiftTypeResponse> =
        shiftTypes.update(wardId, shiftTypeId, request.toCommand(), ifMatch, subject).toEntity(HttpStatus.OK)

    @DeleteMapping("/{shiftTypeId}")
    fun deactivate(
        @PathVariable wardId: UUID,
        @PathVariable shiftTypeId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @CurrentSubject subject: String,
    ): ResponseEntity<ShiftTypeResponse> = shiftTypes.deactivate(wardId, shiftTypeId, ifMatch, subject).toEntity(HttpStatus.OK)

    /** What a PUT with this body would change, without saving. `active: false` previews a deactivation. */
    @PostMapping("/{shiftTypeId}/preview")
    fun preview(
        @PathVariable wardId: UUID,
        @PathVariable shiftTypeId: UUID,
        @Valid @RequestBody request: ShiftTypeRequest,
        @CurrentSubject subject: String,
    ): ShiftTypeChangePreviewResponse = shiftTypes.preview(wardId, shiftTypeId, request.toCommand(), subject).let { p ->
        ShiftTypeChangePreviewResponse(
            p.shiftType.toResponse(), p.policyRevision, p.flagsDrafts, p.rewritesDraftSlotTimes,
            p.affectedDrafts.map { AffectedDraftResponse(it.rosterId, it.month, it.revision, it.slots, it.assignedSlots) },
            p.publishedRostersUsingType,
        )
    }
}

private fun ShiftTypeWrite.toEntity(status: HttpStatus): ResponseEntity<ShiftTypeResponse> =
    ResponseEntity.status(status).eTag(ShiftTypeService.policyEtag(policyRevision)).body(shiftType.toResponse())

private fun ShiftTypeRequest.toCommand() = ShiftTypeCommand(
    code, displayName, kind, LocalTime.parse(startTime), LocalTime.parse(endTime), spansMidnight,
    breakMinutes, recognizedMinutes, targetCreditMinutes, active,
)

private fun ShiftTypeResult.toResponse() = ShiftTypeResponse(
    id, code, displayName, kind.name, start.toString(), end.toString(), spansMidnight, durationMinutes,
    breakMinutes, recognizedMinutes, targetCreditMinutes, active,
)
