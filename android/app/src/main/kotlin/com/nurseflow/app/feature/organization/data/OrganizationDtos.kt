package com.nurseflow.app.feature.organization.data

import kotlinx.serialization.Serializable

/** `docs/openapi.yaml`의 응답·요청 스키마와 같은 이름의 전송 모델. 화면에서 직접 쓰지 않는다. */

@Serializable
data class WardMembershipDto(
    val wardId: String,
    val wardName: String,
    val timeZone: String,
    val hospitalId: String? = null,
    val hospitalName: String? = null,
    val roles: List<String>,
    val canViewWardCalendar: Boolean,
)

@Serializable
data class HospitalDto(val hospitalId: String, val name: String, val timeZone: String, val roles: List<String>)

@Serializable
data class WardDto(
    val wardId: String,
    val hospitalId: String,
    val name: String,
    val timeZone: String,
    val createdAt: String? = null,
)

@Serializable
data class ItemsDto<T>(val items: List<T>)

/** 역할·관리자 대상 필드는 계약에 없고 보내지 않는다. */
@Serializable
data class CreateWardRequestDto(val name: String, val timeZone: String? = null)
