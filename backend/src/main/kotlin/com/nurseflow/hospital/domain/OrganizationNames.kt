package com.nurseflow.hospital.domain

import java.time.DateTimeException
import java.time.ZoneId

/** 병원·병동 이름과 시간대의 입력 규칙. 형식이 틀리면 null을 돌려주고 오류 코드는 서비스가 정한다. */
object OrganizationNames {
    const val MAX_NAME_LENGTH = 100
    private const val MAX_ZONE_LENGTH = 100

    /** 앞뒤 공백을 제거하고 1~100자, 제어 문자 없음만 허용한다. */
    fun normalizeName(raw: String?): String? {
        val trimmed = raw?.trim() ?: return null
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME_LENGTH) return null
        if (trimmed.any { it.isISOControl() }) return null
        return trimmed
    }

    fun parseZone(raw: String?): ZoneId? {
        val value = raw?.trim() ?: return null
        if (value.isEmpty() || value.length > MAX_ZONE_LENGTH) return null
        return try {
            ZoneId.of(value)
        } catch (_: DateTimeException) {
            null
        }
    }
}
