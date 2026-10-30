package com.nurseflow.roster.domain

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

enum class ShiftKind {
    DAY,
    NIGHT,
    OTHER,
}

enum class ApprovedAbsenceKind {
    APPROVED_LEAVE,
    APPROVED_HALF_DAY,
}

enum class RuleSeverity {
    HARD,
    SOFT,
}

/**
 * shiftDate is the calendar date in the ward's configured timezone. The exact
 * ward timezone is supplied when shift instances are created; this validator
 * never guesses it from the server timezone.
 */
data class ShiftAssignment(
    val assignmentId: UUID?,
    val nurseId: UUID,
    val shiftDate: LocalDate,
    val kind: ShiftKind,
    val startsAt: Instant,
    val endsAt: Instant,
)

/** Stores only the approved time interval and category, never a leave reason. */
data class ApprovedAbsence(
    val nurseId: UUID,
    val kind: ApprovedAbsenceKind,
    val startsAt: Instant,
    val endsAt: Instant,
)

data class RuleViolation(
    val code: String,
    val severity: RuleSeverity,
    val message: String,
    val affectedDate: LocalDate? = null,
)

/**
 * Deterministic hard-rule validation for a single candidate assignment.
 * This is a domain component only; persistence, ward authorization, and API
 * response mapping belong in later application/service layers.
 */
class RosterRuleValidator {
    fun validate(
        candidate: ShiftAssignment,
        existingAssignments: Collection<ShiftAssignment>,
        approvedAbsences: Collection<ApprovedAbsence>,
    ): List<RuleViolation> {
        if (!candidate.startsAt.isBefore(candidate.endsAt)) {
            return listOf(
                RuleViolation(
                    code = "INVALID_SHIFT_INTERVAL",
                    severity = RuleSeverity.HARD,
                    message = "근무 종료 시각은 시작 시각보다 뒤여야 합니다.",
                    affectedDate = candidate.shiftDate,
                ),
            )
        }

        val violations = mutableListOf<RuleViolation>()

        approvedAbsences
            .asSequence()
            .filter { it.nurseId == candidate.nurseId }
            .filter { overlaps(candidate.startsAt, candidate.endsAt, it.startsAt, it.endsAt) }
            .forEach { absence ->
                val code = when (absence.kind) {
                    ApprovedAbsenceKind.APPROVED_LEAVE -> "APPROVED_LEAVE_OVERLAP"
                    ApprovedAbsenceKind.APPROVED_HALF_DAY -> "APPROVED_HALF_DAY_OVERLAP"
                }
                val message = when (absence.kind) {
                    ApprovedAbsenceKind.APPROVED_LEAVE -> "이미 등록된 승인 휴가 시간과 겹칩니다."
                    ApprovedAbsenceKind.APPROVED_HALF_DAY -> "이미 등록된 반차 시간과 겹칩니다."
                }
                violations += RuleViolation(
                    code = code,
                    severity = RuleSeverity.HARD,
                    message = message,
                    affectedDate = candidate.shiftDate,
                )
            }

        existingAssignments
            .asSequence()
            .filter { it.nurseId == candidate.nurseId }
            .filterNot { candidate.assignmentId != null && it.assignmentId == candidate.assignmentId }
            .filter { overlaps(candidate.startsAt, candidate.endsAt, it.startsAt, it.endsAt) }
            .forEach {
                violations += RuleViolation(
                    code = "SHIFT_TIME_OVERLAP",
                    severity = RuleSeverity.HARD,
                    message = "같은 시간대에 다른 근무가 이미 배정되어 있습니다.",
                    affectedDate = candidate.shiftDate,
                )
            }

        val nightToDayConflict = existingAssignments.any { existing ->
            existing.nurseId == candidate.nurseId &&
                when (candidate.kind) {
                    ShiftKind.DAY ->
                        existing.kind == ShiftKind.NIGHT &&
                            existing.shiftDate.plusDays(1) == candidate.shiftDate

                    ShiftKind.NIGHT ->
                        existing.kind == ShiftKind.DAY &&
                            candidate.shiftDate.plusDays(1) == existing.shiftDate

                    ShiftKind.OTHER -> false
                }
        }

        if (nightToDayConflict) {
            violations += RuleViolation(
                code = "REST_AFTER_NIGHT",
                severity = RuleSeverity.HARD,
                message = "나이트 근무 다음 날에는 데이 근무를 배치할 수 없습니다.",
                affectedDate = candidate.shiftDate,
            )
        }

        return violations.distinctBy { it.code to it.affectedDate }
    }

    /** Uses half-open intervals [start, end), so adjacent shifts do not overlap. */
    private fun overlaps(
        firstStart: Instant,
        firstEnd: Instant,
        secondStart: Instant,
        secondEnd: Instant,
    ): Boolean = firstStart < secondEnd && secondStart < firstEnd
}
