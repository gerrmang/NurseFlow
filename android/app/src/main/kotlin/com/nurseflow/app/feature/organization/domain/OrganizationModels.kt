package com.nurseflow.app.feature.organization.domain

import com.nurseflow.app.core.network.ApiResult

/** 본인이 소속된 병동. 병동 선택 화면의 선택 대상이다. */
data class MyWard(
    val wardId: String,
    val wardName: String,
    val timeZone: String,
    val hospitalId: String?,
    val hospitalName: String?,
    val roles: Set<String>,
    val canViewWardCalendar: Boolean,
) {
    val isWardAdmin: Boolean get() = "WARD_ADMIN" in roles
}

/** 본인이 병원 관리자인 병원. */
data class Hospital(val hospitalId: String, val name: String, val timeZone: String)

/** 병원 관리자에게 보이는 병원 소속 병동. 소속이 없으면 근무표 권한은 없다. */
data class HospitalWard(val wardId: String, val hospitalId: String, val name: String, val timeZone: String)

interface OrganizationRepository {
    suspend fun myWards(): ApiResult<List<MyWard>>
    suspend fun myHospitals(): ApiResult<List<Hospital>>
    suspend fun hospitalWards(hospitalId: String): ApiResult<List<HospitalWard>>

    /** 같은 [idempotencyKey]·같은 입력의 재시도는 서버가 처음 결과를 돌려준다. */
    suspend fun createWard(hospitalId: String, name: String, timeZone: String?, idempotencyKey: String): ApiResult<HospitalWard>
}

/** 서버 `OrganizationNames`와 같은 즉시 입력 확인. 최종 판정은 서버가 한다. */
object WardNameRules {
    const val MAX_LENGTH = 100

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_LENGTH) return null
        if (trimmed.any { it.isISOControl() }) return null
        return trimmed
    }
}
