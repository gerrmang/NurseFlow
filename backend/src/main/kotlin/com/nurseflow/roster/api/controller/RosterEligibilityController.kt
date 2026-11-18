package com.nurseflow.roster.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.policy.application.service.ShiftTypeService
import com.nurseflow.roster.application.service.EligibilityResult
import com.nurseflow.roster.application.service.RosterEligibilityService
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

data class DemoModeRequest(val demoMode: Boolean)
data class EligibilityItemResponse(val nurseId: UUID, val displayName: String, val eligible: Boolean, val reason: String)
data class EligibilityResponse(val demoMode: Boolean, val policyRevision: Int, val items: List<EligibilityItemResponse>)

@RestController
@RequestMapping("/api/v1/wards/{wardId}/roster-eligibility")
class RosterEligibilityController(private val service: RosterEligibilityService) {
    @GetMapping
    fun get(@PathVariable wardId: UUID, @CurrentSubject actor: String): ResponseEntity<EligibilityResponse> =
        service.get(wardId, actor).toEntity()

    @PutMapping
    fun setDemoMode(
        @PathVariable wardId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @Valid @RequestBody request: DemoModeRequest,
        @CurrentSubject actor: String,
    ): ResponseEntity<EligibilityResponse> = service.setDemoMode(wardId, request.demoMode, ifMatch, actor).toEntity()
}

private fun EligibilityResult.toEntity(): ResponseEntity<EligibilityResponse> =
    ResponseEntity.ok().eTag(ShiftTypeService.policyEtag(policyRevision)).header("Cache-Control", "private, no-store")
        .body(EligibilityResponse(demoMode, policyRevision, items.map {
            EligibilityItemResponse(it.nurseId, it.displayName, it.eligible, it.reason)
        }))
