package com.nurseflow.hospital.api.dto.request

/**
 * 병동 생성 요청. 역할이나 관리자 subject 필드는 의도적으로 없다.
 * 알 수 없는 필드(예: `role`, `adminSubject`)는 전역 Jackson 설정으로 400 `INVALID_REQUEST`가 된다.
 * 필드 형식 검증은 서비스의 업무 오류 코드(`INVALID_WARD_NAME`, `INVALID_TIME_ZONE`)로 처리한다.
 */
data class CreateWardRequest(
    val name: String? = null,
    val timeZone: String? = null,
)
