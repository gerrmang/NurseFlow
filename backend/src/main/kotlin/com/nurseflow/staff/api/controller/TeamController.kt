package com.nurseflow.staff.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.staff.api.dto.request.CareerPolicyRequest
import com.nurseflow.staff.api.dto.request.TeamRequest
import com.nurseflow.staff.api.dto.response.CareerPolicyResponse
import com.nurseflow.staff.api.dto.response.TeamItems
import com.nurseflow.staff.api.dto.response.TeamResponse
import com.nurseflow.staff.application.service.StaffService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Ward teams and career policy. Members may read them; only admins change them. */
@RestController
@RequestMapping("/api/v1/wards/{wardId}")
class TeamController(private val staff: StaffService) {
    @GetMapping("/teams")
    fun teams(@PathVariable wardId: UUID, @CurrentSubject subject: String): TeamItems =
        TeamItems(staff.teams(wardId, subject).map { it.toResponse() })

    @PostMapping("/teams")
    fun createTeam(
        @PathVariable wardId: UUID,
        @Valid @RequestBody request: TeamRequest,
        @CurrentSubject subject: String,
    ): ResponseEntity<TeamResponse> =
        ResponseEntity.status(HttpStatus.CREATED).body(staff.createTeam(wardId, request.name, subject).toResponse())

    @PutMapping("/teams/{teamId}")
    fun updateTeam(
        @PathVariable wardId: UUID,
        @PathVariable teamId: UUID,
        @Valid @RequestBody request: TeamRequest,
        @CurrentSubject subject: String,
    ): TeamResponse = staff.updateTeam(wardId, teamId, request.name, request.sortOrder, subject).toResponse()

    @DeleteMapping("/teams/{teamId}")
    fun deleteTeam(@PathVariable wardId: UUID, @PathVariable teamId: UUID, @CurrentSubject subject: String): ResponseEntity<Void> {
        staff.deleteTeam(wardId, teamId, subject)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/career-policy")
    fun careerPolicy(@PathVariable wardId: UUID, @CurrentSubject subject: String): CareerPolicyResponse =
        staff.careerPolicy(wardId, subject).toResponse()

    @PutMapping("/career-policy")
    fun updateCareerPolicy(
        @PathVariable wardId: UUID,
        @Valid @RequestBody request: CareerPolicyRequest,
        @CurrentSubject subject: String,
    ): CareerPolicyResponse =
        staff.updateCareerPolicy(wardId, request.stageThresholdMonths, request.ranks, subject).toResponse()
}
