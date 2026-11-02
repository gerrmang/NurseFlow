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
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

@RestController
@RequestMapping("/api/v1/wards")
class WardController(private val wards: WardService) {
    @GetMapping
    fun myWards(@CurrentSubject subject: String): WardMembershipItems =
        WardMembershipItems(wards.membershipsOf(subject).map { it.toResponse() })

    /** Creates a ward; the caller becomes its WARD_ADMIN. Nurses join only through invitations. */
    @PostMapping
    fun create(
        @Valid @RequestBody request: CreateWardRequest,
        @CurrentSubject subject: String,
    ): ResponseEntity<WardMembershipResponse> = ResponseEntity.status(HttpStatus.CREATED).body(
        wards.createWard(
            CreateWardCommand(request.name, request.timeZone, YearMonth.parse(request.careerStartedOn), request.rank.trim()),
            subject,
        )
            .toResponse(),
    )
}

internal fun WardMembershipResult.toResponse() =
    WardMembershipResponse(wardId, wardName, roles.map { it.name }, canViewWardCalendar)
