package com.nurseflow.ward.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.ward.api.dto.request.CreateWardRequest
import com.nurseflow.ward.api.dto.request.UpdateMembershipRequest
import com.nurseflow.ward.api.dto.response.WardMemberItems
import com.nurseflow.ward.api.dto.response.WardMemberResponse
import com.nurseflow.ward.application.model.UpdateMembershipCommand
import com.nurseflow.ward.application.model.WardMemberResult
import com.nurseflow.ward.application.service.MembershipService
import com.nurseflow.ward.api.dto.response.WardMembershipItems
import com.nurseflow.ward.api.dto.response.WardMembershipResponse
import com.nurseflow.ward.application.model.CreateWardCommand
import com.nurseflow.ward.application.model.WardMembershipResult
import com.nurseflow.ward.application.service.WardService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/wards")
class WardController(private val wards: WardService, private val members: MembershipService) {
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
        val command = CreateWardCommand(hospital.source, hospital.externalId, request.wardName, request.timeZone)
        return ResponseEntity.status(HttpStatus.CREATED).body(wards.createWard(command, idempotencyKey, subject).toResponse())
    }

    @GetMapping("/{wardId}/members")
    fun listMembers(@PathVariable wardId: UUID, @CurrentSubject subject: String): ResponseEntity<WardMemberItems> =
        ResponseEntity.ok().header("Cache-Control", "private, no-store")
            .body(WardMemberItems(members.list(wardId, subject).map { it.toResponse() }))

    @PatchMapping("/{wardId}/memberships/{userId}")
    fun updateMembership(
        @PathVariable wardId: UUID,
        @PathVariable userId: UUID,
        @RequestBody request: UpdateMembershipRequest,
        @CurrentSubject subject: String,
    ): ResponseEntity<WardMemberResponse> {
        val command = UpdateMembershipCommand(request.admin, request.active)
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
            .body(members.update(wardId, userId, command, subject).toResponse())
    }
}

internal fun WardMemberResult.toResponse() =
    WardMemberResponse(userId, displayName, roles.map { it.name }, active, canViewWardCalendar, nurseId)

internal fun WardMembershipResult.toResponse() =
    WardMembershipResponse(wardId, wardName, roles.map { it.name }, canViewWardCalendar, hospitalName)
