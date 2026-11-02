package com.nurseflow.hospital.persistence.entity

import java.time.Instant
import java.time.ZoneId
import java.util.UUID

data class DbHospital(val id: UUID, val name: String, val zone: ZoneId, val createdAt: Instant)

data class DbHospitalWard(
    val id: UUID,
    val hospitalId: UUID,
    val name: String,
    val zone: ZoneId,
    val createdAt: Instant?,
)

data class NewWard(
    val id: UUID,
    val hospitalId: UUID,
    val name: String,
    val zone: ZoneId,
    val createdAt: Instant,
    val createdBy: String,
)

/** 멱등성 키 조회 결과. HTTP 오류 판단은 서비스가 한다. */
sealed interface ReplayLookup {
    data object Absent : ReplayLookup
    data object Mismatch : ReplayLookup
    data class Match(val responseJson: String) : ReplayLookup
}
