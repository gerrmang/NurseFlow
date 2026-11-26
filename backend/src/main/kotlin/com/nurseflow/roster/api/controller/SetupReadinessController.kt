package com.nurseflow.roster.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.roster.api.dto.response.GenerationPrecheckResponse
import com.nurseflow.roster.api.dto.response.ReadinessItemResponse
import com.nurseflow.roster.api.dto.response.SeniorCoverageResponse
import com.nurseflow.roster.api.dto.response.WardSetupReadinessResponse
import com.nurseflow.roster.application.service.SetupReadinessService
import com.nurseflow.roster.domain.readiness.ReadinessItem
import com.nurseflow.roster.domain.readiness.ReadinessStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Ward setup readiness and the draft's pre-generation check (A18). Admin only, read only. */
@RestController
class SetupReadinessController(private val service: SetupReadinessService) {
    @GetMapping("/api/v1/wards/{wardId}/setup-readiness")
    fun ward(@PathVariable wardId: UUID, @CurrentSubject actor: String): ResponseEntity<WardSetupReadinessResponse> {
        val r = service.ward(wardId, actor)
        return noStore(
            WardSetupReadinessResponse(
                r.wardId, r.ready, r.items.count { it.status == ReadinessStatus.COMPLETE }, r.items.size, r.items.map { it.toResponse() },
            ),
        )
    }

    @GetMapping("/api/v1/rosters/{rosterId}/generation-precheck")
    fun precheck(@PathVariable rosterId: UUID, @CurrentSubject actor: String): ResponseEntity<GenerationPrecheckResponse> {
        val p = service.precheck(rosterId, actor)
        return noStore(
            GenerationPrecheckResponse(
                p.rosterId, p.wardId, p.month.toString(), p.revision, p.ready, p.items.map { it.toResponse() },
                p.maxShiftsPerNurse,
                p.seniorCoverage.map {
                    SeniorCoverageResponse(
                        it.teamId, it.teamName, it.shiftKind?.name, it.requiredSeniorSlots, it.availableSeniors,
                        it.seniorShiftCapacity, it.shortage, it.reasons.map { r -> r.name }, it.firstShortDate,
                    )
                },
            ),
        )
    }

    // No ETag: the result depends on ward settings, staff and holidays as well as the roster.
    private fun <T : Any> noStore(body: T): ResponseEntity<T> =
        ResponseEntity.ok().header("Cache-Control", "private, no-store").body(body)

    private fun ReadinessItem.toResponse() = ReadinessItemResponse(key.name, status.name, blocking, message, missing)
}
