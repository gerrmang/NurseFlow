package com.nurseflow.roster.persistence.entity

import java.time.Instant
import java.util.UUID

/**
 * generation_jobs 행. staffing/diagnostics는 저장된 JSON 원문을 그대로 보관하고
 * API 응답 형태로의 역직렬화는 application 계층이 담당한다.
 */
data class DbGenerationJob(
    val jobId: UUID,
    val rosterId: UUID,
    val status: String,
    val sourceRevision: Int,
    val createdAt: Instant,
    val resultRevision: Int?,
    val staffingJson: String?,
    val diagnosticsJson: String?,
    val finishedAt: Instant?,
)
