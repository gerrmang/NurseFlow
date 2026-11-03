package com.nurseflow.ward.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.ward.api.dto.request.BootstrapWardRequest
import com.nurseflow.ward.api.dto.response.WardMembershipResponse
import com.nurseflow.ward.application.model.BootstrapWardCommand
import com.nurseflow.ward.application.model.WardMembershipResult
import com.nurseflow.ward.application.service.WardService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/wards")
class WardController(private val wards: WardService) {
    /** Demo-only first ward and admin. Disabled unless NURSEFLOW_BOOTSTRAP_CODE is set. */
    @PostMapping
    fun bootstrap(
        @Valid @RequestBody request: BootstrapWardRequest,
        @CurrentSubject subject: String,
    ): ResponseEntity<WardMembershipResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        wards.bootstrapWard(BootstrapWardCommand(request.name, request.timeZone, request.bootstrapCode), subject)
            .toResponse(),
    )
}

internal fun WardMembershipResult.toResponse() =
    WardMembershipResponse(wardId, wardName, roles.map { it.name }, canViewWardCalendar)
