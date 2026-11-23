package com.nurseflow.roster.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.policy.application.service.ShiftTypeService
import com.nurseflow.roster.api.dto.request.ConsecutiveWorkPolicyRequest
import com.nurseflow.roster.api.dto.response.ConsecutiveWorkPolicyResponse
import com.nurseflow.roster.application.service.ConsecutiveWorkPolicy
import com.nurseflow.roster.application.service.ConsecutiveWorkPolicyService
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/wards/{wardId}/consecutive-work-policy")
class ConsecutiveWorkPolicyController(private val service: ConsecutiveWorkPolicyService) {
    @GetMapping
    fun get(@PathVariable wardId: UUID, @CurrentSubject actor: String) =
        service.get(wardId, actor).toEntity()

    @PutMapping
    fun replace(
        @PathVariable wardId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @Valid @RequestBody request: ConsecutiveWorkPolicyRequest,
        @CurrentSubject actor: String,
    ) = service.replace(wardId, request.maxConsecutiveWorkDays, ifMatch, actor).toEntity()
}

private fun ConsecutiveWorkPolicy.toEntity() = ResponseEntity.ok()
    .eTag(ShiftTypeService.policyEtag(policyRevision))
    .header("Cache-Control", "private, no-store")
    .body(ConsecutiveWorkPolicyResponse(maxConsecutiveWorkDays, policyRevision))
