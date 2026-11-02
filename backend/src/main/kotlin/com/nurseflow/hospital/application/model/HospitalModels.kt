package com.nurseflow.hospital.application.model

import java.time.Instant
import java.util.UUID

/** 병동 생성 명령. 역할·관리자 대상은 요청에서 받지 않는다. 최초 병동 관리자는 인증된 요청자다. */
data class CreateWardCommand(
    val hospitalId: UUID,
    val name: String?,
    val timeZone: String?,
    val idempotencyKey: UUID,
)

data class HospitalResult(val hospitalId: UUID, val name: String, val timeZone: String, val roles: List<String>)

data class WardResult(
    val wardId: UUID,
    val hospitalId: UUID,
    val name: String,
    val timeZone: String,
    val createdAt: Instant?,
)

/** 운영자 전용 초기 설정 명령. HTTP나 클라이언트 입력으로 만들지 않는다. */
data class BootstrapHospitalCommand(val name: String?, val timeZone: String?, val adminSubject: String?)

data class BootstrapHospitalResult(val hospitalId: UUID, val created: Boolean)
