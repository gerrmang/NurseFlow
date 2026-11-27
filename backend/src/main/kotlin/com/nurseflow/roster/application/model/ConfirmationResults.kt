package com.nurseflow.roster.application.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** What a confirm needs acknowledged (A16). Each list is one card: 0 items means the card is hidden. */
enum class AcknowledgementKind { GENERAL_UNFILLED, SENIOR_UNFILLED, HOUR_SHORTFALL }

data class UnfilledSlotDiagnostic(
    val slotId: UUID,
    val date: LocalDate,
    val shiftTypeId: UUID,
    val shiftCode: String,
    val teamId: UUID?,
    val reason: String,
    val message: String,
)

data class HourShortfallDiagnostic(
    val nurseId: UUID,
    val displayName: String,
    val targetMinutes: Int,
    val creditMinutes: Int,
    val shortfallMinutes: Int,
    val reason: String,
    val message: String,
)

/**
 * The server's confirm-time diagnosis of a roster. [blockingViolations] are hard rule violations: acknowledging
 * the other lists never confirms a roster that has any.
 */
data class ConfirmationCheck(
    val rosterId: UUID,
    val status: String,
    val revision: Int,
    val hasRequirements: Boolean,
    val blockingViolations: List<RuleDiagnostic>,
    val generalUnfilled: List<UnfilledSlotDiagnostic>,
    val seniorUnfilled: List<UnfilledSlotDiagnostic>,
    val hourShortfalls: List<HourShortfallDiagnostic>,
) {
    val confirmable: Boolean get() = status == "DRAFT" && hasRequirements && blockingViolations.isEmpty()

    /** The request that acknowledges exactly what this check shows. */
    fun acknowledgeAll() = ConfirmRosterRequest(
        ConfirmAcknowledgements(
            generalUnfilled.map { it.slotId },
            seniorUnfilled.map { it.slotId },
            hourShortfalls.map { HourShortfallAcknowledgement(it.nurseId, it.shortfallMinutes) },
        ),
    )
}

/** The acknowledgement snapshot stored with a published revision. */
data class ConfirmationRecord(
    val rosterId: UUID,
    val month: String,
    val revision: Int,
    val confirmedAt: Instant,
    val generalUnfilled: List<UnfilledSlotDiagnostic>,
    val seniorUnfilled: List<UnfilledSlotDiagnostic>,
    val hourShortfalls: List<HourShortfallDiagnostic>,
)

/**
 * Server reason codes for acknowledged items. A16 states why an item needs acknowledging; a finer cause per slot
 * (which rule kept every candidate out) belongs to the generation diagnostics of A19.
 */
object ConfirmationReasons {
    const val UNFILLED_SLOT = "UNFILLED_SLOT"
    const val SENIOR_UNFILLED = "SENIOR_UNFILLED"
    const val BELOW_MONTHLY_TARGET = "BELOW_MONTHLY_TARGET"

    fun message(reason: String): String = when (reason) {
        UNFILLED_SLOT -> "배치된 간호사가 없는 근무 칸입니다."
        SENIOR_UNFILLED -> "선임 자격자가 배치되지 않은 선임 필수 칸입니다."
        BELOW_MONTHLY_TARGET -> "배치된 근무와 인정 부재를 합해도 월 목표시간에 못 미칩니다."
        else -> reason
    }
}
