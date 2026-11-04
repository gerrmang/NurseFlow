package com.nurseflow.ward.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.ward.api.dto.response.WardMembershipItems
import com.nurseflow.ward.api.dto.response.WardMembershipResponse
import com.nurseflow.ward.application.model.WardMembershipResult
import com.nurseflow.ward.application.service.WardService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/wards")
class WardController(private val wards: WardService) {
    @GetMapping
    fun myWards(@CurrentSubject subject: String): WardMembershipItems =
        WardMembershipItems(wards.membershipsOf(subject).map { it.toResponse() })

}

internal fun WardMembershipResult.toResponse() =
    WardMembershipResponse(wardId, wardName, roles.map { it.name }, canViewWardCalendar)
