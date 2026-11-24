package com.nurseflow.offrequest.api.dto.response

import com.nurseflow.offrequest.domain.OffRequest
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Account subjects of the submitter and decider stay on the server; `source` says who entered it. */
data class OffRequestResponse(
    val requestId: UUID,
    val nurseId: UUID,
    val date: LocalDate,
    val status: String,
    val source: String,
    val submittedAt: Instant,
    val decidedAt: Instant?,
    val afterDeadline: Boolean,
) {
    companion object {
        fun from(request: OffRequest) = OffRequestResponse(
            request.id, request.nurseId, request.date, request.status.name, request.source.name,
            request.submittedAt, request.decidedAt, request.afterDeadline,
        )
    }
}

/** `deadline` is exclusive: nurse entries from this instant on are exception requests. */
data class OffRequestList(val items: List<OffRequestResponse>, val deadline: Instant, val monthlyLimit: Int)

data class OffRequestPolicyResponse(val monthlyLimit: Int, val deadlineDay: Int, val policyRevision: Int)
