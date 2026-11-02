package com.nurseflow.hospital.application.error

import com.nurseflow.common.error.ApiException
import org.springframework.http.HttpStatus

/**
 * 병원·병동 기능의 안정적인 오류 코드.
 *
 * 중앙 `ErrorCode`/`BusinessException` 도입 PR(`fix/backend-conventions-rest`)이 아직 `dev`에 병합되지 않아
 * 공통 오류 파일과 충돌하지 않도록 기능 안에 둔다. 해당 PR 병합 후 같은 이름·상태로 중앙 enum에 옮긴다.
 * 서비스는 HTTP 상태나 문자열 코드를 직접 만들지 않고 이 enum만 사용한다.
 */
enum class HospitalErrorCode(val status: HttpStatus, val safeMessage: String) {
    SUBJECT_REQUIRED(HttpStatus.UNAUTHORIZED, "사용자 식별 정보가 없습니다."),
    HOSPITAL_NOT_FOUND(HttpStatus.NOT_FOUND, "병원을 찾을 수 없습니다."),
    HOSPITAL_ADMIN_REQUIRED(HttpStatus.FORBIDDEN, "병원 관리자 권한이 필요합니다."),
    WARD_NOT_FOUND(HttpStatus.NOT_FOUND, "병동을 찾을 수 없습니다."),
    INVALID_WARD_NAME(HttpStatus.BAD_REQUEST, "병동 이름은 1~100자여야 합니다."),
    INVALID_TIME_ZONE(HttpStatus.BAD_REQUEST, "시간대가 올바르지 않습니다."),
    WARD_NAME_TAKEN(HttpStatus.CONFLICT, "같은 병원에 같은 이름의 병동이 있습니다."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.CONFLICT, "다른 요청에 이미 사용한 키입니다."),
    ;

    fun exception(): ApiException = ApiException(status, name, safeMessage)
}

fun fail(code: HospitalErrorCode): Nothing = throw code.exception()
