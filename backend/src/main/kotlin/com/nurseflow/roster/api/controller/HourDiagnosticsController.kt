package com.nurseflow.roster.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.policy.api.controller.toResponse
import com.nurseflow.roster.api.dto.response.HourDiagnosticsResponse
import com.nurseflow.roster.api.dto.response.NurseHoursResponse
import com.nurseflow.roster.application.service.HourDiagnosticsService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Per-nurse monthly hours of a roster against the ward target (A13). Admin only, read only. */
@RestController
class HourDiagnosticsController(private val service: HourDiagnosticsService) {
    @GetMapping("/api/v1/rosters/{rosterId}/hour-diagnostics")
    fun get(@PathVariable rosterId: UUID, @CurrentSubject actor: String): ResponseEntity<HourDiagnosticsResponse> {
        val d = service.diagnose(rosterId, actor)
        // No ETag: the result also depends on the ward policy, calendar and neighbouring published months.
        return ResponseEntity.ok()
            .header("Cache-Control", "private, no-store")
            .body(
                HourDiagnosticsResponse(
                    d.rosterId, d.month.toString(), d.status, d.revision, d.policyRevision, d.target.toResponse(),
                    d.policy.nightDedicatedTargetMinutes ?: d.target.generalTargetMinutes,
                    NIGHT_ATTRIBUTION, d.previousMonthPublished,
                    d.nurses.map {
                        NurseHoursResponse(
                            it.nurse.nurseId, it.nurse.displayName, it.nurse.workPattern.name, it.nurse.rosterable,
                            it.shiftCount, it.actualMinutes, it.shiftCreditMinutes, it.leaveDays, it.halfDays, it.sofDays,
                            it.absenceCreditMinutes, it.creditMinutes, it.targetMinutes, it.remainingMinutes,
                            maxOf(0, -it.remainingMinutes), maxOf(0, it.remainingMinutes), it.status.name,
                            it.carriedInShifts, it.carriedOutShifts,
                        )
                    },
                ),
            )
    }

    private companion object {
        const val NIGHT_ATTRIBUTION = "END_DATE_MONTH"
    }
}
