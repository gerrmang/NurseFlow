package com.nurseflow.staff.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.staff.api.dto.request.CareerProfileRequest
import com.nurseflow.staff.api.dto.request.CareerStartRequest
import com.nurseflow.staff.api.dto.request.StaffRequest
import com.nurseflow.staff.api.dto.request.TeamAssignmentRequest
import com.nurseflow.staff.api.dto.response.StaffMemberResponse
import com.nurseflow.staff.api.dto.response.StaffPage
import com.nurseflow.staff.application.service.StaffService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/wards/{wardId}")
class StaffController(private val staff: StaffService) {
    /** Admin roster pool: team order, then most experienced first. */
    @GetMapping("/staff")
    fun list(
        @PathVariable wardId: UUID,
        @RequestParam(required = false) teamId: UUID?,
        @RequestParam(defaultValue = "false") includeInactive: Boolean,
        @CurrentSubject subject: String,
    ): ResponseEntity<StaffPage> {
        val items = staff.staff(wardId, subject, teamId, includeInactive).map { it.toResponse() }
        return ResponseEntity.ok().header("Cache-Control", "private, no-store")
            .body(StaffPage(items, page = 1, pageSize = items.size, totalItems = items.size))
    }

    /** Fictional nurse record an invitation can later link to a real account. */
    @PostMapping("/staff")
    fun create(
        @PathVariable wardId: UUID,
        @Valid @RequestBody request: StaffRequest,
        @CurrentSubject subject: String,
    ): ResponseEntity<StaffMemberResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staff.createStaff(wardId, request.toCommand(), subject).toResponse())

    /** Name, qualifications and active flag. `active=false` removes the nurse from rostering. */
    @PutMapping("/staff/{nurseId}")
    fun update(
        @PathVariable wardId: UUID,
        @PathVariable nurseId: UUID,
        @Valid @RequestBody request: StaffRequest,
        @CurrentSubject subject: String,
    ): StaffMemberResponse = staff.updateStaff(wardId, nurseId, request.toCommand(), subject).toResponse()

    @PutMapping("/staff/{nurseId}/team")
    fun assignTeam(
        @PathVariable wardId: UUID,
        @PathVariable nurseId: UUID,
        @Valid @RequestBody request: TeamAssignmentRequest,
        @CurrentSubject subject: String,
    ): StaffMemberResponse = staff.assignTeam(wardId, nurseId, request.teamId, subject).toResponse()

    @PutMapping("/staff/{nurseId}/profile")
    fun setProfile(
        @PathVariable wardId: UUID,
        @PathVariable nurseId: UUID,
        @Valid @RequestBody request: CareerProfileRequest,
        @CurrentSubject subject: String,
    ): StaffMemberResponse = staff.setProfileAsAdmin(wardId, nurseId, request.toInput(), subject).toResponse()

    @PostMapping("/staff/{nurseId}/profile/confirm")
    fun confirmProfile(
        @PathVariable wardId: UUID,
        @PathVariable nurseId: UUID,
        @CurrentSubject subject: String,
    ): StaffMemberResponse = staff.confirmProfile(wardId, nurseId, subject).toResponse()

    /** The signed-in nurse's own record. Editing resets confirmation until an admin re-confirms. */
    @GetMapping("/my-profile")
    fun myProfile(@PathVariable wardId: UUID, @CurrentSubject subject: String): StaffMemberResponse =
        staff.myProfile(wardId, subject).toResponse()

    @PutMapping("/my-profile")
    fun submitMyProfile(
        @PathVariable wardId: UUID,
        @Valid @RequestBody request: CareerStartRequest,
        @CurrentSubject subject: String,
    ): StaffMemberResponse = staff.submitMyProfile(wardId, request.toCareerStart(), subject).toResponse()
}
