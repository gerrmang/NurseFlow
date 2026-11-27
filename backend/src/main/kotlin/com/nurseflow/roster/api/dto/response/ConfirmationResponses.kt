package com.nurseflow.roster.api.dto.response

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** A16: one card per category. `count` 0 means the card is hidden and needs no acknowledgement. */
data class ConfirmationCheckResponse(
    val rosterId: UUID,
    val status: String,
    val revision: Int,
    val confirmable: Boolean,
    val blockingViolations: List<RuleDiagnostic>,
    val generalUnfilled: UnfilledAcknowledgementGroup,
    val seniorUnfilled: UnfilledAcknowledgementGroup,
    val hourShortfalls: HourShortfallAcknowledgementGroup,
)

data class ConfirmationRecordResponse(
    val rosterId: UUID,
    val month: String,
    val revision: Int,
    val confirmedAt: Instant,
    val generalUnfilled: UnfilledAcknowledgementGroup,
    val seniorUnfilled: UnfilledAcknowledgementGroup,
    val hourShortfalls: HourShortfallAcknowledgementGroup,
)

data class UnfilledAcknowledgementGroup(val count: Int, val items: List<UnfilledSlotAcknowledgementItem>)

data class UnfilledSlotAcknowledgementItem(
    val slotId: UUID,
    val date: LocalDate,
    val shiftTypeId: UUID,
    val shiftCode: String,
    val teamId: UUID?,
    val reason: String,
    val message: String,
)

data class HourShortfallAcknowledgementGroup(
    val count: Int,
    val totalShortfallMinutes: Int,
    val items: List<HourShortfallAcknowledgementItem>,
)

data class HourShortfallAcknowledgementItem(
    val nurseId: UUID,
    val displayName: String,
    val targetMinutes: Int,
    val creditMinutes: Int,
    val shortfallMinutes: Int,
    val reason: String,
    val message: String,
)
