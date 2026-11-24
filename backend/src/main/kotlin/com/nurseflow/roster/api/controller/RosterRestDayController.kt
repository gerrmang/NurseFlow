package com.nurseflow.roster.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.roster.api.dto.request.ReplaceRestDaysRequest
import com.nurseflow.roster.api.dto.response.Items
import com.nurseflow.roster.api.dto.response.RestDayResponse
import com.nurseflow.roster.application.service.RosterRestDays
import com.nurseflow.roster.application.service.RosterService
import com.nurseflow.roster.domain.model.RestDayKind
import com.nurseflow.roster.domain.model.RosterRestDay
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/rosters/{rosterId}/rest-days")
class RosterRestDayController(private val service: RosterService) {
    @GetMapping
    fun list(@PathVariable rosterId: UUID, @CurrentSubject actor: String) =
        service.listRestDays(rosterId, actor).toEntity(rosterId)

    @PutMapping
    fun replace(
        @PathVariable rosterId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @Valid @RequestBody request: ReplaceRestDaysRequest,
        @CurrentSubject actor: String,
    ) = service.replaceRestDays(rosterId, ifMatch, request.items.map {
        RosterRestDay(it.nurseId, it.date, RestDayKind.valueOf(it.kind))
    }, actor).toEntity(rosterId)

    private fun RosterRestDays.toEntity(rosterId: UUID) = ResponseEntity.ok()
        .eTag(service.etag(rosterId, revision))
        .header("Cache-Control", "private, no-store")
        .body(Items(items.map { RestDayResponse(it.nurseId, it.date, it.kind.name) }))
}
