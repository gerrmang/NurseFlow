package com.nurseflow.roster.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.policy.api.controller.toResponse
import com.nurseflow.roster.api.dto.request.CreateNightDedicatedTermRequest
import com.nurseflow.roster.api.dto.request.EndNightDedicatedTermRequest
import com.nurseflow.roster.api.dto.response.DedicatedHeadcountResponse
import com.nurseflow.roster.api.dto.response.Items
import com.nurseflow.roster.api.dto.response.NightDedicatedTermResponse
import com.nurseflow.roster.api.dto.response.NightDiagnosticsResponse
import com.nurseflow.roster.api.dto.response.NurseNightResponse
import com.nurseflow.roster.application.service.NightDedicatedTermService
import com.nurseflow.roster.application.service.NightDiagnosticsService
import com.nurseflow.roster.domain.model.NightDedicatedTerm
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Night-dedicated rotation records and a roster's SOF/Night diagnostics (A15). Admin only. */
@RestController
class NightDedicatedController(
    private val terms: NightDedicatedTermService,
    private val diagnostics: NightDiagnosticsService,
) {
    @GetMapping("/api/v1/wards/{wardId}/night-dedicated-terms")
    fun list(
        @PathVariable wardId: UUID,
        @RequestParam(defaultValue = "false") includeCancelled: Boolean,
        @CurrentSubject actor: String,
    ): ResponseEntity<Items<NightDedicatedTermResponse>> = ResponseEntity.ok().header("Cache-Control", "private, no-store")
        .body(Items(terms.list(wardId, includeCancelled, actor).map { it.toResponse() }))

    @PostMapping("/api/v1/wards/{wardId}/night-dedicated-terms")
    fun create(
        @PathVariable wardId: UUID,
        @RequestBody request: CreateNightDedicatedTermRequest,
        @CurrentSubject actor: String,
    ): ResponseEntity<NightDedicatedTermResponse> {
        val grant = terms.create(wardId, request.nurseId, request.startMonth, request.endMonth, actor)
        return ResponseEntity.status(if (grant.created) HttpStatus.CREATED else HttpStatus.OK).body(grant.term.toResponse())
    }

    @PostMapping("/api/v1/wards/{wardId}/night-dedicated-terms/{termId}/end")
    fun end(
        @PathVariable wardId: UUID,
        @PathVariable termId: UUID,
        @RequestBody request: EndNightDedicatedTermRequest,
        @CurrentSubject actor: String,
    ): NightDedicatedTermResponse = terms.end(wardId, termId, request.lastMonth, actor).toResponse()

    @GetMapping("/api/v1/rosters/{rosterId}/night-diagnostics")
    fun diagnose(@PathVariable rosterId: UUID, @CurrentSubject actor: String): ResponseEntity<NightDiagnosticsResponse> {
        val d = diagnostics.diagnose(rosterId, actor)
        // No ETag: the result also depends on the ward's Night policy and dedicated terms.
        return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(
            NightDiagnosticsResponse(
                d.rosterId, d.month.toString(), d.status, d.revision, d.policyRevision, d.policy.toResponse(d.policyRevision),
                NIGHT_ATTRIBUTION,
                d.diagnosis.headcount.let { DedicatedHeadcountResponse(it.required, it.assigned, it.status.name, it.nurseIds) },
                d.diagnosis.nurses.map {
                    NurseNightResponse(
                        it.nurse.nurseId, it.nurse.displayName, it.nurse.workPattern.name, it.nurse.rosterable,
                        it.nightCount, it.sofDays, it.dedicated, it.dedicatedTermId, it.dedicatedRunMonths,
                        it.sofRequiredDays, it.sofStatus.name, it.dedicatedNightStatus?.name, it.nightShortfall,
                        it.issues.map { issue -> issue.name },
                    )
                },
            ),
        )
    }

    private companion object {
        /** Nights count in the month of the date they start on, unlike hours (decision 14, END_DATE_MONTH). */
        const val NIGHT_ATTRIBUTION = "SHIFT_DATE"
    }
}

private fun NightDedicatedTerm.toResponse() = NightDedicatedTermResponse(
    id, nurseId, startMonth.toString(), plannedEndMonth.toString(), endMonth.toString(), status.name, createdAt, endedAt,
)
