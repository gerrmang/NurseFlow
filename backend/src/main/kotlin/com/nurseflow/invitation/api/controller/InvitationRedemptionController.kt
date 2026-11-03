package com.nurseflow.invitation.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.invitation.api.dto.request.InvitationTokenRequest
import com.nurseflow.invitation.api.dto.response.InvitationPreviewResponse
import com.nurseflow.invitation.application.service.InvitationRedemptionService
import com.nurseflow.ward.api.controller.toResponse
import com.nurseflow.ward.api.dto.response.WardMembershipResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Token travels in the body (never the URL) so it stays out of access logs. Login is required. */
@RestController
@RequestMapping("/api/v1/invitations")
class InvitationRedemptionController(private val redemption: InvitationRedemptionService) {
    @PostMapping("/preview")
    fun preview(
        @Valid @RequestBody request: InvitationTokenRequest,
        @CurrentSubject subject: String,
    ): ResponseEntity<InvitationPreviewResponse> {
        val preview = redemption.preview(request.token)
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(
            InvitationPreviewResponse(preview.wardId, preview.wardName, preview.role.name, preview.expiresAt, preview.linksExistingNurse),
        )
    }

    @PostMapping("/redeem")
    fun redeem(
        @Valid @RequestBody request: InvitationTokenRequest,
        @CurrentSubject subject: String,
    ): ResponseEntity<WardMembershipResponse> = ResponseEntity.status(HttpStatus.CREATED)
        .header("Cache-Control", "private, no-store")
        .body(redemption.redeem(request.token, subject).toResponse())
}
