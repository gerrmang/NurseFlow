package com.nurseflow.offrequest.api.controller

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.security.CurrentSubject
import com.nurseflow.offrequest.api.dto.request.OffRequestPolicyRequest
import com.nurseflow.offrequest.api.dto.request.SubmitOffRequestRequest
import com.nurseflow.offrequest.api.dto.response.OffRequestList
import com.nurseflow.offrequest.api.dto.response.OffRequestPolicyResponse
import com.nurseflow.offrequest.api.dto.response.OffRequestResponse
import com.nurseflow.offrequest.application.service.OffRequestPolicySnapshot
import com.nurseflow.offrequest.application.service.OffRequestService
import com.nurseflow.policy.application.service.ShiftTypeService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth
import java.util.UUID

@RestController
@RequestMapping("/api/v1/wards/{wardId}/off-requests")
class OffRequestController(private val service: OffRequestService) {
    /** Admins see the whole ward's requests for the month; a nurse sees only their own. */
    @GetMapping
    fun list(@PathVariable wardId: UUID, @RequestParam month: String, @CurrentSubject actor: String) =
        service.list(wardId, parseMonth(month), actor).let {
            noStore(OffRequestList(it.items.map(OffRequestResponse::from), it.deadline, it.policy.monthlyLimit))
        }

    @PostMapping
    fun submit(
        @PathVariable wardId: UUID,
        @RequestBody request: SubmitOffRequestRequest,
        @CurrentSubject actor: String,
    ): ResponseEntity<OffRequestResponse> = ResponseEntity.status(HttpStatus.CREATED)
        .header("Cache-Control", "private, no-store")
        .body(OffRequestResponse.from(service.submit(wardId, request.nurseId, request.date, actor)))

    @PostMapping("/{requestId}/confirm")
    fun confirm(@PathVariable wardId: UUID, @PathVariable requestId: UUID, @CurrentSubject actor: String) =
        noStore(OffRequestResponse.from(service.confirm(wardId, requestId, actor)))

    @PostMapping("/{requestId}/reject")
    fun reject(@PathVariable wardId: UUID, @PathVariable requestId: UUID, @CurrentSubject actor: String) =
        noStore(OffRequestResponse.from(service.reject(wardId, requestId, actor)))

    @PostMapping("/{requestId}/withdraw")
    fun withdraw(@PathVariable wardId: UUID, @PathVariable requestId: UUID, @CurrentSubject actor: String) =
        noStore(OffRequestResponse.from(service.withdraw(wardId, requestId, actor)))

    @GetMapping("/policy")
    fun policy(@PathVariable wardId: UUID, @CurrentSubject actor: String) = service.getPolicy(wardId, actor).toEntity()

    @PutMapping("/policy")
    fun replacePolicy(
        @PathVariable wardId: UUID,
        @RequestHeader("If-Match") ifMatch: String,
        @RequestBody request: OffRequestPolicyRequest,
        @CurrentSubject actor: String,
    ) = service.replacePolicy(wardId, request.limit(), request.day(), ifMatch, actor).toEntity()

    private fun OffRequestPolicySnapshot.toEntity() = ResponseEntity.ok()
        .eTag(ShiftTypeService.policyEtag(policyRevision))
        .header("Cache-Control", "private, no-store")
        .body(OffRequestPolicyResponse(policy.monthlyLimit, policy.deadlineDay, policyRevision))

    private fun <T : Any> noStore(body: T): ResponseEntity<T> =
        ResponseEntity.ok().header("Cache-Control", "private, no-store").body(body)

    private fun parseMonth(value: String): YearMonth {
        if (!MONTH.matches(value)) throw BusinessException(ErrorCode.INVALID_MONTH)
        return YearMonth.parse(value)
    }

    private companion object {
        val MONTH = Regex("^\\d{4}-(0[1-9]|1[0-2])$")
    }
}
