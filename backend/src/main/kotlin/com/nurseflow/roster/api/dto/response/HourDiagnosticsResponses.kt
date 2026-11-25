package com.nurseflow.roster.api.dto.response

import com.nurseflow.policy.api.dto.response.MonthTargetResponse
import java.util.UUID

data class HourDiagnosticsResponse(
    val rosterId: UUID,
    val month: String,
    val status: String,
    val revision: Int,
    val policyRevision: Int,
    val target: MonthTargetResponse,
    val nightDedicatedTargetMinutes: Int,
    val nightAttribution: String,
    val previousMonthPublished: Boolean,
    val items: List<NurseHoursResponse>,
)

data class NurseHoursResponse(
    val nurseId: UUID,
    val displayName: String,
    val workPattern: String,
    val rosterable: Boolean,
    val shiftCount: Int,
    val actualMinutes: Int,
    val shiftCreditMinutes: Int,
    val leaveDays: Int,
    val halfDays: Int,
    val sofDays: Int,
    val absenceCreditMinutes: Int,
    val creditMinutes: Int,
    val targetMinutes: Int,
    val remainingMinutes: Int,
    val shortfallMinutes: Int,
    val overtimeMinutes: Int,
    val status: String,
    val carriedInShifts: Int,
    val carriedOutShifts: Int,
)
