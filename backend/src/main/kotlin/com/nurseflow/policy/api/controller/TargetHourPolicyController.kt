package com.nurseflow.policy.api.controller

import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.policy.api.dto.request.ReplaceHolidayCalendarRequest
import com.nurseflow.policy.api.dto.request.TargetHourPolicyRequest
import com.nurseflow.policy.api.dto.response.HolidayCalendarDayResponse
import com.nurseflow.policy.api.dto.response.HolidayCalendarResponse
import com.nurseflow.policy.api.dto.response.HolidaySourceResponse
import com.nurseflow.policy.api.dto.response.MonthTargetResponse
import com.nurseflow.policy.api.dto.response.TargetHourPolicyResponse
import com.nurseflow.policy.application.service.HolidayCalendarResult
import com.nurseflow.policy.application.service.ShiftTypeService
import com.nurseflow.policy.application.service.TargetHourPolicyResult
import com.nurseflow.policy.application.service.TargetHourPolicyService
import com.nurseflow.policy.domain.MonthTarget
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/** Ward target-hour policy and holiday calendar (A13). Admin only; writes require the ward policy ETag. */
@RestController
class TargetHourPolicyController(private val service: TargetHourPolicyService) {
    @GetMapping("/api/v1/wards/{wardId}/target-hour-policy")
    fun get(@PathVariable wardId: UUID, @CurrentSubject actor: String) = service.get(wardId, actor).toEntity()

    @PutMapping("/api/v1/wards/{wardId}/target-hour-policy")
    fun replace(
        @PathVariable wardId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @RequestBody request: TargetHourPolicyRequest,
        @CurrentSubject actor: String,
    ) = service.replace(wardId, request::toPolicy, ifMatch, actor).toEntity()

    @GetMapping("/api/v1/wards/{wardId}/holiday-calendar")
    fun calendar(@PathVariable wardId: UUID, @RequestParam month: String, @CurrentSubject actor: String) =
        service.calendar(wardId, month, actor).toEntity()

    /** Fetches the month's national holidays from the public source now (also refreshed daily when a key is set). */
    @PostMapping("/api/v1/wards/{wardId}/holiday-calendar/refresh")
    fun refreshCalendar(@PathVariable wardId: UUID, @RequestParam month: String, @CurrentSubject actor: String) =
        service.refreshCalendar(wardId, month, actor).toEntity()

    @PutMapping("/api/v1/wards/{wardId}/holiday-calendar")
    fun replaceCalendar(
        @PathVariable wardId: UUID,
        @RequestParam month: String,
        @RequestHeader("If-Match") ifMatch: String,
        @Valid @RequestBody request: ReplaceHolidayCalendarRequest,
        @CurrentSubject actor: String,
    ) = service.replaceCalendar(wardId, month, request.toDays(), ifMatch, actor).toEntity()
}

private fun TargetHourPolicyResult.toEntity() = ResponseEntity.ok()
    .eTag(ShiftTypeService.policyEtag(policyRevision))
    .header("Cache-Control", "private, no-store")
    .body(
        TargetHourPolicyResponse(
            policy.dailyStandardMinutes, policy.leaveCreditMinutes, policy.halfDayCreditMinutes, policy.sofCreditMinutes,
            policy.nightDedicatedTargetMinutes, policy.nightDedicatedNightCreditMinutes, policyRevision,
        ),
    )

private fun HolidayCalendarResult.toEntity() = ResponseEntity.ok()
    .eTag(ShiftTypeService.policyEtag(policyRevision))
    .header("Cache-Control", "private, no-store")
    .body(
        HolidayCalendarResponse(
            target.month.toString(), days.map { HolidayCalendarDayResponse(it.date, it.kind.name, it.name) },
            publicHolidays.map { HolidayCalendarDayResponse(it.date, it.kind.name, it.name) },
            HolidaySourceResponse(sourceConfigured, publicHolidaysFetchedAt),
            target.toResponse(), policyRevision,
        ),
    )

fun MonthTarget.toResponse() = MonthTargetResponse(
    month.toString(), daysInMonth, weekendDays, holidayDays, wardRestDays, wardWorkdays, restDays, targetDays,
    dailyStandardMinutes, generalTargetMinutes,
)
