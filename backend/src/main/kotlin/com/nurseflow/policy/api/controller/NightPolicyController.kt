package com.nurseflow.policy.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.policy.api.dto.request.NightPolicyRequest
import com.nurseflow.policy.api.dto.response.NightPolicyResponse
import com.nurseflow.policy.application.service.NightPolicyResult
import com.nurseflow.policy.application.service.NightPolicyService
import com.nurseflow.policy.application.service.ShiftTypeService
import com.nurseflow.policy.domain.NightPolicy
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Ward SOF and Night-dedicated settings (A15). Admin only; writes require the ward policy ETag. */
@RestController
@RequestMapping("/api/v1/wards/{wardId}/night-policy")
class NightPolicyController(private val service: NightPolicyService) {
    @GetMapping
    fun get(@PathVariable wardId: UUID, @CurrentSubject actor: String) = service.get(wardId, actor).toEntity()

    @PutMapping
    fun replace(
        @PathVariable wardId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @RequestBody request: NightPolicyRequest,
        @CurrentSubject actor: String,
    ) = service.replace(wardId, request::toPolicy, ifMatch, actor).toEntity()
}

private fun NightPolicyResult.toEntity() = ResponseEntity.ok()
    .eTag(ShiftTypeService.policyEtag(policyRevision))
    .header("Cache-Control", "private, no-store")
    .body(policy.toResponse(policyRevision))

fun NightPolicy.toResponse(policyRevision: Int) = NightPolicyResponse(
    sofNightThreshold, sofDays, dedicatedHeadcount, dedicatedRotationMonths, dedicatedMinNights, policyRevision,
)
