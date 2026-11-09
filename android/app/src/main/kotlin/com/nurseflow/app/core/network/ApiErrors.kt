package com.nurseflow.app.core.network

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/**
 * A failure the UI can show. `code` is the server's stable error code (see ErrorCode on the
 * server); screens branch on it, never on the message text.
 */
class ApiException(val status: Int, val code: String?, override val message: String) : Exception(message)

/** Runs an API call and converts transport/HTTP failures into [ApiException]. */
suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: ApiException) {
    throw e
} catch (e: HttpException) {
    throw e.toApiException()
} catch (_: IOException) {
    throw ApiException(0, "NETWORK", "서버에 연결할 수 없어요. 네트워크와 서버 주소를 확인해 주세요.")
}

private fun HttpException.toApiException(): ApiException {
    val problem = response()?.errorBody()?.string()?.let { body ->
        runCatching { ApiJson.json.decodeFromString(ApiProblemDto.serializer(), body) }.getOrNull()
    }
    val fallback = when (code()) {
        401 -> "로그인이 필요해요."
        403 -> "권한이 없어요."
        404 -> "찾을 수 없어요."
        in 500..599 -> "서버에 일시적인 문제가 있어요. 잠시 후 다시 시도해 주세요."
        else -> "요청을 처리하지 못했어요."
    }
    return ApiException(code(), problem?.code, fallback)
}

fun Throwable.userMessage(): String = (this as? ApiException)?.message ?: "알 수 없는 오류가 발생했어요."
