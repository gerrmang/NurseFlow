package com.nurseflow.app.core.network

/** 서버 호출 결과. 화면은 HTTP 상태 대신 이 분류와 서버의 안정적인 오류 `code`로 상태를 정한다. */
sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    /** 이 빌드에 서버 주소가 설정되지 않았다(릴리스 빌드의 현재 상태). */
    data object NotConfigured : ApiError

    /** 로그인 세션이 없다. 실제 Kakao 로그인은 아직 연결되지 않았다. */
    data object SignedOut : ApiError

    /** 401. 토큰이 만료되었거나 유효하지 않다. */
    data object SessionExpired : ApiError

    /** 403. 로그인은 되었지만 권한이 없다. */
    data class Forbidden(val code: String?) : ApiError

    /** 404. 리소스가 없거나 접근할 수 없어 존재를 숨겼다. */
    data class NotFound(val code: String?) : ApiError

    /** 400·409·412·422 등 사용자가 고칠 수 있는 요청 오류. */
    data class Rejected(val status: Int, val code: String?) : ApiError

    /** 연결 실패·시간 초과. 요청이 서버에 도달했는지 알 수 없으므로 같은 멱등 키로 재시도한다. */
    data object Network : ApiError

    /** 5xx. 서버가 처리 결과를 확정하지 못했을 수 있다. 같은 멱등 키로 재시도한다. */
    data class Server(val status: Int) : ApiError

    /**
     * 2xx 응답을 받았지만 본문을 계약대로 해석하지 못했다. 쓰기 요청이면 서버가 이미 처리했을 수 있지만
     * 결과를 확인할 수 없으므로 결과 불명으로 다루고 같은 멱등 키로 재시도한다.
     */
    data object UnexpectedResponse : ApiError
}

/**
 * 요청 결과가 불확실해 같은 요청을 같은 멱등 키로 다시 보내야 하는 오류인가.
 * 통신 실패·5xx·해석할 수 없는 2xx가 해당한다. 확정된 4xx·401은 해당하지 않는다.
 */
val ApiError.outcomeUnknown: Boolean
    get() = this is ApiError.Network || this is ApiError.Server || this is ApiError.UnexpectedResponse
