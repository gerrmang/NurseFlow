package com.nurseflow.policy.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.policy.api.dto.request.ShiftTypeRequest
import com.nurseflow.policy.api.dto.response.ShiftTypeItems
import com.nurseflow.policy.api.dto.response.ShiftTypeResponse
import com.nurseflow.policy.application.model.ShiftTypeCommand
import com.nurseflow.policy.application.model.ShiftTypeResult
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

/** Ward shift types. Members read them; only admins change them. */
@RestController
@RequestMapping("/api/v1/wards/{wardId}/shift-types")
class ShiftTypeController(private val shiftTypes: ShiftTypeService) {
    @GetMapping
    fun list(
        @PathVariable wardId: UUID,
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
        @CurrentSubject subject: String,
    ): ShiftTypeItems = shiftTypes.list(wardId, includeInactive, subject).let { result ->
        ShiftTypeItems(result.items.map { it.toResponse() }, result.policyRevision)
    }

    @PostMapping
    fun create(
        @PathVariable wardId: UUID,
        @Valid @RequestBody request: ShiftTypeRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @CurrentSubject subject: String,
    ): ResponseEntity<ShiftTypeResponse> = ResponseEntity.status(HttpStatus.CREATED)
        .body(shiftTypes.create(wardId, request.toCommand(), idempotencyKey, subject).toResponse())

    @PutMapping("/{shiftTypeId}")
    fun update(
        @PathVariable wardId: UUID,
        @PathVariable shiftTypeId: UUID,
        @Valid @RequestBody request: ShiftTypeRequest,
        @CurrentSubject subject: String,
    ): ShiftTypeResponse = shiftTypes.update(wardId, shiftTypeId, request.toCommand(), subject).toResponse()

    @DeleteMapping("/{shiftTypeId}")
    fun deactivate(
        @PathVariable wardId: UUID,
        @PathVariable shiftTypeId: UUID,
        @CurrentSubject subject: String,
    ): ShiftTypeResponse = shiftTypes.deactivate(wardId, shiftTypeId, subject).toResponse()
}

private fun ShiftTypeRequest.toCommand() = ShiftTypeCommand(
    code, displayName, kind, LocalTime.parse(startTime), LocalTime.parse(endTime), spansMidnight,
    breakMinutes, recognizedMinutes, targetCreditMinutes, active,
)

private fun ShiftTypeResult.toResponse() = ShiftTypeResponse(
    id, code, displayName, kind.name, start.toString(), end.toString(), spansMidnight, durationMinutes,
    breakMinutes, recognizedMinutes, targetCreditMinutes, active,
)
