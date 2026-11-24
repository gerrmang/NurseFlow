package com.nurseflow.roster.api.dto.response

import java.time.LocalDate
import java.util.UUID

data class ConsecutiveWorkPolicyResponse(val maxConsecutiveWorkDays: Int, val policyRevision: Int)

data class RestDayResponse(val nurseId: UUID, val date: LocalDate, val kind: String)
