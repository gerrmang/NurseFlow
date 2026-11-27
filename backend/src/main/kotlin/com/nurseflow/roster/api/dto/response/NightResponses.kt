package com.nurseflow.roster.api.dto.response

import com.nurseflow.policy.api.dto.response.NightPolicyResponse
import java.time.Instant
import java.util.UUID

data class NightDedicatedTermResponse(
    val termId: UUID,
    val nurseId: UUID,
    val startMonth: String,
    val plannedEndMonth: String,
    val endMonth: String,
    val status: String,
    val createdAt: Instant,
    val endedAt: Instant?,
)

data class NightDiagnosticsResponse(
    val rosterId: UUID,
    val month: String,
    val status: String,
    val revision: Int,
    val policyRevision: Int,
    val policy: NightPolicyResponse,
    val nightAttribution: String,
    val dedicatedHeadcount: DedicatedHeadcountResponse,
    val items: List<NurseNightResponse>,
)

data class DedicatedHeadcountResponse(
    val required: Int,
    val assigned: Int,
    val status: String,
    val nurseIds: List<UUID>,
)

data class NurseNightResponse(
    val nurseId: UUID,
    val displayName: String,
    val workPattern: String,
    val rosterable: Boolean,
    val nightCount: Int,
    val sofDays: Int,
    val dedicated: Boolean,
    val dedicatedTermId: UUID?,
    val dedicatedRunMonths: Int,
    val sofRequiredDays: Int,
    val sofStatus: String,
    val dedicatedNightStatus: String?,
    val nightShortfall: Int,
    val issues: List<String>,
)
