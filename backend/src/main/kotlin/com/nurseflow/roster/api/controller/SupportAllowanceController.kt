package com.nurseflow.roster.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.roster.api.dto.request.CreateSupportAllowanceRequest
import com.nurseflow.roster.api.dto.response.Items
import com.nurseflow.roster.api.dto.response.SupportAllowanceResponse
import com.nurseflow.roster.api.dto.response.SupportAllowanceRevocationResponse
import com.nurseflow.roster.application.service.SupportAllowanceInput
import com.nurseflow.roster.application.service.SupportAllowanceService
import com.nurseflow.roster.domain.model.SupportAllowance
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Admin-granted cross-team support (A14). Only ward admins read or change it. */
@RestController
@RequestMapping("/api/v1/wards/{wardId}/support-allowances")
class SupportAllowanceController(private val service: SupportAllowanceService) {
    @GetMapping
    fun list(
        @PathVariable wardId: UUID,
        @RequestParam(defaultValue = "false") includeRevoked: Boolean,
        @CurrentSubject actor: String,
    ): ResponseEntity<Items<SupportAllowanceResponse>> = ResponseEntity.ok().header("Cache-Control", "private, no-store")
        .body(Items(service.list(wardId, includeRevoked, actor).map { it.toResponse() }))

    @PostMapping
    fun create(
        @PathVariable wardId: UUID,
        @Valid @RequestBody request: CreateSupportAllowanceRequest,
        @CurrentSubject actor: String,
    ): ResponseEntity<SupportAllowanceResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        service.create(wardId, SupportAllowanceInput(
            request.nurseId, request.teamId, request.startDate, request.endDate, request.shiftTypeIds,
        ), actor).toResponse(),
    )

    @PostMapping("/{allowanceId}/revoke")
    fun revoke(
        @PathVariable wardId: UUID,
        @PathVariable allowanceId: UUID,
        @CurrentSubject actor: String,
    ): SupportAllowanceRevocationResponse = service.revoke(wardId, allowanceId, actor).let {
        SupportAllowanceRevocationResponse(it.allowance.toResponse(), it.flaggedDrafts)
    }
}

private fun SupportAllowance.toResponse() = SupportAllowanceResponse(
    id, nurseId, teamId, startDate, endDate, shiftTypeIds.sortedBy(UUID::toString),
    if (revoked) "REVOKED" else "ACTIVE", createdAt, revokedAt,
)
