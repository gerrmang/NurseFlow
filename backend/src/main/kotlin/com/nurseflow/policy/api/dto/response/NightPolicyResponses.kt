package com.nurseflow.policy.api.dto.response

data class NightPolicyResponse(
    val sofNightThreshold: Int,
    val sofDays: Int,
    val dedicatedHeadcount: Int,
    val dedicatedRotationMonths: Int,
    val dedicatedMinNights: Int,
    val policyRevision: Int,
)
