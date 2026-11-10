package com.nurseflow.ward.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.ward.api.dto.request.CreateWardRequest
import com.nurseflow.ward.api.dto.response.WardMembershipItems
import com.nurseflow.ward.api.dto.response.WardMembershipResponse
import com.nurseflow.ward.application.model.CreateWardCommand
import com.nurseflow.ward.application.model.WardMembershipResult
import com.nurseflow.ward.application.service.WardService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/wards")
class WardController(private val wards: WardService) {
    @GetMapping
    fun myWards(@CurrentSubject subject: String): WardMembershipItems =
        WardMembershipItems(wards.membershipsOf(subject).map { it.toResponse() })

    /** Creates a ward for a hospital picked from the official list; the caller becomes its WARD_ADMIN. */
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateWardRequest,
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @CurrentSubject subject: String,
    ): ResponseEntity<WardMembershipResponse> {
        val hospital = request.hospital!!
        val command = CreateWardCommand(hospital.source, hospital.externalId.trim(), request.wardName, request.timeZone)
        return ResponseEntity.status(HttpStatus.CREATED).body(wards.createWard(command, idempotencyKey, subject).toResponse())
    }
}

internal fun WardMembershipResult.toResponse() =
    WardMembershipResponse(wardId, wardName, roles.map { it.name }, canViewWardCalendar, hospitalName)
