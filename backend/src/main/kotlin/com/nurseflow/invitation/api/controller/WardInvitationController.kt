package com.nurseflow.invitation.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.invitation.api.dto.request.CreateInvitationRequest
import com.nurseflow.invitation.api.dto.response.CreatedInvitationResponse
import com.nurseflow.invitation.api.dto.response.InvitationItems
import com.nurseflow.invitation.api.dto.response.InvitationResponse
import com.nurseflow.invitation.application.model.CreateInvitationCommand
import com.nurseflow.invitation.application.model.InvitationResult
import com.nurseflow.invitation.application.service.InvitationService
import com.nurseflow.ward.application.model.WardRole
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/api/v1/wards/{wardId}/invitations")
class WardInvitationController(private val invitations: InvitationService) {
    @PostMapping
    fun create(
        @PathVariable wardId: UUID,
        @Valid @RequestBody request: CreateInvitationRequest,
        @CurrentSubject subject: String,
    ): ResponseEntity<CreatedInvitationResponse> {
        val issued = invitations.create(
            wardId,
            CreateInvitationCommand(WardRole.valueOf(request.role), request.nurseId, Duration.ofHours(request.expiresInHours.toLong())),
            subject,
        )
        return ResponseEntity.status(HttpStatus.CREATED).header("Cache-Control", "no-store")
            .body(CreatedInvitationResponse(issued.invitation.toResponse(), issued.token, issued.inviteUrl))
    }

    @GetMapping
    fun list(@PathVariable wardId: UUID, @CurrentSubject subject: String): ResponseEntity<InvitationItems> =
        ResponseEntity.ok().header("Cache-Control", "private, no-store")
            .body(InvitationItems(invitations.list(wardId, subject).map { it.toResponse() }))

    @DeleteMapping("/{invitationId}")
    fun revoke(
        @PathVariable wardId: UUID,
        @PathVariable invitationId: UUID,
        @CurrentSubject subject: String,
    ): ResponseEntity<Void> {
        invitations.revoke(wardId, invitationId, subject)
        return ResponseEntity.noContent().build()
    }
}

internal fun InvitationResult.toResponse() = InvitationResponse(
    invitationId, wardId, role.name, nurseId, status.name, createdAt, expiresAt, redeemedAt, revokedAt,
)
