package com.nurseflow.app.feature.ward.create

import com.nurseflow.app.core.network.ApiException

/** Hospital search / ward creation failures, classified by the server's stable `code` first, then status. */
enum class WardCreateFailure(val searchMessage: String, val createMessage: String) {
    NETWORK("네트워크 연결을 확인하고 다시 검색해 주세요.", "서버에 연결할 수 없어요."),
    /** Search 503 HOSPITAL_SEARCH_UNAVAILABLE: the external lookup is down; retry later. */
    UNAVAILABLE("병원 정보 서비스에 일시적으로 연결할 수 없어요. 잠시 후 다시 검색해 주세요.", "병원 정보 서비스에 일시적으로 연결할 수 없어요."),
    NOT_CONFIGURED("서버에 병원 목록이 아직 준비되지 않았어요. 준비된 뒤에 이용할 수 있어요.", "서버에 병원 목록이 아직 준비되지 않아 병동을 만들 수 없어요."),
    INVALID_REQUEST("검색어를 확인해 주세요.", "병동 이름을 확인해 주세요."),
    HOSPITAL_NOT_FOUND("선택한 병원을 찾을 수 없어요. 다시 검색해 주세요.", "선택한 병원을 찾을 수 없어요. 다시 검색해 주세요."),
    ALREADY_CREATED("이미 만든 병동이에요.", "같은 병원·이름의 병동을 이미 만들었어요. 병동 목록에서 확인해 주세요."),
    LIMIT_REACHED("만들 수 있는 병동 수에 도달했어요.", "만들 수 있는 병동 수에 도달했어요."),
    KEY_REUSED("다시 시도해 주세요.", "요청을 구분하는 값이 겹쳤어요. 다시 시도해 주세요."),
    /** USER_NOT_FOUND: the token is valid but the account is gone; the local session must end. */
    USER_NOT_FOUND("계정 정보를 찾을 수 없어요. 다시 로그인해 주세요.", "계정 정보를 찾을 수 없어요. 다시 로그인해 주세요."),
    UNAUTHORIZED("로그인이 필요해요.", "로그인이 필요해요."),
    SERVER("서버에 일시적인 문제가 있어요. 잠시 후 다시 검색해 주세요.", "서버에 일시적인 문제가 있어요."),
    UNKNOWN("요청을 처리하지 못했어요.", "요청을 처리하지 못했어요."),
    ;

    /** The server may have processed a write: retry with the same key, never as a different request. */
    val outcomeUnknown: Boolean get() = this == NETWORK || this == SERVER || this == UNKNOWN
    val endsSession: Boolean get() = this == USER_NOT_FOUND
}

fun Throwable.toWardCreateFailure(): WardCreateFailure {
    val e = this as? ApiException ?: return WardCreateFailure.UNKNOWN
    return when (e.code) {
        "HOSPITAL_SEARCH_UNAVAILABLE" -> WardCreateFailure.UNAVAILABLE
        "HOSPITAL_SEARCH_NOT_CONFIGURED" -> WardCreateFailure.NOT_CONFIGURED
        "INVALID_REQUEST" -> WardCreateFailure.INVALID_REQUEST
        "HOSPITAL_NOT_FOUND" -> WardCreateFailure.HOSPITAL_NOT_FOUND
        "WARD_ALREADY_CREATED" -> WardCreateFailure.ALREADY_CREATED
        "WARD_CREATION_LIMIT_REACHED" -> WardCreateFailure.LIMIT_REACHED
        "IDEMPOTENCY_KEY_REUSED" -> WardCreateFailure.KEY_REUSED
        "USER_NOT_FOUND" -> WardCreateFailure.USER_NOT_FOUND
        else -> when {
            e.status == 401 -> WardCreateFailure.UNAUTHORIZED
            e.status == 0 -> WardCreateFailure.NETWORK
            e.status in 500..599 -> WardCreateFailure.SERVER
            else -> WardCreateFailure.UNKNOWN
        }
    }
}
