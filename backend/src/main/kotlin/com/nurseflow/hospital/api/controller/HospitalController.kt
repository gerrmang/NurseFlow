package com.nurseflow.hospital.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.hospital.api.dto.request.CreateWardRequest
import com.nurseflow.hospital.api.dto.response.HospitalResponse
import com.nurseflow.hospital.api.dto.response.ItemsResponse
import com.nurseflow.hospital.api.dto.response.WardResponse
import com.nurseflow.hospital.application.model.CreateWardCommand
import com.nurseflow.hospital.application.model.HospitalResult
import com.nurseflow.hospital.application.model.WardResult
import com.nurseflow.hospital.application.service.HospitalWardService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.util.UUID

@RestController
@RequestMapping("/api/v1/hospitals")
class HospitalController(private val service: HospitalWardService) {

    @GetMapping
    fun myHospitals(@CurrentSubject subject: String): ResponseEntity<ItemsResponse<HospitalResponse>> =
        noStore(ItemsResponse(service.listMyHospitals(subject).map(::hospitalResponse)))

    @GetMapping("/{hospitalId}/wards")
    fun wards(@PathVariable hospitalId: UUID, @CurrentSubject subject: String): ResponseEntity<ItemsResponse<WardResponse>> =
        noStore(ItemsResponse(service.listWards(subject, hospitalId).map(::wardResponse)))

    @GetMapping("/{hospitalId}/wards/{wardId}")
    fun ward(
        @PathVariable hospitalId: UUID,
        @PathVariable wardId: UUID,
        @CurrentSubject subject: String,
    ): ResponseEntity<WardResponse> = noStore(wardResponse(service.getWard(subject, hospitalId, wardId)))

    @PostMapping("/{hospitalId}/wards")
    fun createWard(
        @PathVariable hospitalId: UUID,
        @RequestHeader("Idempotency-Key") idempotencyKey: UUID,
        @RequestBody request: CreateWardRequest,
        @CurrentSubject subject: String,
    ): ResponseEntity<WardResponse> {
        val result = service.createWard(subject, CreateWardCommand(hospitalId, request.name, request.timeZone, idempotencyKey))
        return ResponseEntity.created(URI.create("/api/v1/hospitals/${result.hospitalId}/wards/${result.wardId}"))
            .header("Cache-Control", "private, no-store")
            .body(wardResponse(result))
    }

    private fun <T : Any> noStore(body: T): ResponseEntity<T> =
        ResponseEntity.ok().header("Cache-Control", "private, no-store").body(body)

    private fun hospitalResponse(result: HospitalResult) =
        HospitalResponse(result.hospitalId, result.name, result.timeZone, result.roles)

    private fun wardResponse(result: WardResult) =
        WardResponse(result.wardId, result.hospitalId, result.name, result.timeZone, result.createdAt)
}
