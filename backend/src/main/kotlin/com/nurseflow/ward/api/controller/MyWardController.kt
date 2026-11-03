package com.nurseflow.ward.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.ward.api.dto.response.MyWardListResponse
import com.nurseflow.ward.api.dto.response.MyWardResponse
import com.nurseflow.ward.application.service.MyWardService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class MyWardController(private val service: MyWardService) {
    @GetMapping("/api/v1/wards")
    fun myWards(@CurrentSubject subject: String): ResponseEntity<MyWardListResponse> = ResponseEntity.ok()
        .header("Cache-Control", "private, no-store")
        .body(MyWardListResponse(service.myWards(subject).map {
            MyWardResponse(it.wardId, it.wardName, it.timeZone, it.hospitalId, it.hospitalName, it.roles, it.canViewWardCalendar)
        }))
}
