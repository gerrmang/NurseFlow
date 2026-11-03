package com.nurseflow.auth.api.controller

import com.nurseflow.auth.api.dto.response.CurrentUserResponse
import com.nurseflow.auth.application.service.AuthService
import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.ward.api.controller.toResponse
import com.nurseflow.ward.application.service.WardService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class MeController(private val auth: AuthService, private val wards: WardService) {
    /** Signed-in user and server-side ward memberships. Empty memberships = invitation required. */
    @GetMapping("/me")
    fun me(@CurrentSubject subject: String): ResponseEntity<CurrentUserResponse> {
        val user = auth.requireUser(subject)
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(
            CurrentUserResponse(user.id, user.displayName, wards.membershipsOf(subject).map { it.toResponse() }),
        )
    }
}
