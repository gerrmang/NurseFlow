package com.nurseflow.app.core.network

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import java.io.IOException

/** 현재 로그인 세션의 access token. 없으면 null. 토큰 값은 로그·화면에 남기지 않는다. */
fun interface AccessTokenProvider {
    fun currentToken(): String?
}

/**
 * NurseFlow API 공통 호출기. 인증 헤더 부착, 서버 `application/problem+json` 오류 해석, 통신 실패 분류만 담당한다.
 * 화면 상태나 기능별 모델을 알지 못한다. 블로킹 호출이므로 저장소가 IO 디스패처에서 실행한다.
 */
class ApiClient(
    private val baseUrl: String,
    private val tokens: AccessTokenProvider,
    private val transport: HttpTransport,
    val json: Json = defaultJson,
) {
    fun <T> get(path: String, response: DeserializationStrategy<T>): ApiResult<T> =
        call(HttpRequest("GET", path), response)

    fun <B, T> post(
        path: String,
        body: B,
        bodySerializer: SerializationStrategy<B>,
        response: DeserializationStrategy<T>,
        headers: Map<String, String> = emptyMap(),
    ): ApiResult<T> = call(
        HttpRequest("POST", path, headers + ("Content-Type" to "application/json"), json.encodeToString(bodySerializer, body)),
        response,
    )

    private fun <T> call(request: HttpRequest, response: DeserializationStrategy<T>): ApiResult<T> {
        if (baseUrl.isBlank()) return ApiResult.Failure(ApiError.NotConfigured)
        val token = tokens.currentToken()?.takeIf { it.isNotBlank() } ?: return ApiResult.Failure(ApiError.SignedOut)
        val authorized = request.copy(headers = request.headers + mapOf("Authorization" to "Bearer $token", "Accept" to "application/json"))
        val result = try {
            transport.execute(baseUrl, authorized)
        } catch (_: IOException) {
            return ApiResult.Failure(ApiError.Network)
        }
        if (result.status in 200..299) {
            return try {
                ApiResult.Success(json.decodeFromString(response, result.body))
            } catch (_: SerializationException) {
                ApiResult.Failure(ApiError.UnexpectedResponse)
            } catch (_: IllegalArgumentException) {
                ApiResult.Failure(ApiError.UnexpectedResponse)
            }
        }
        return ApiResult.Failure(errorFor(result))
    }

    private fun errorFor(response: HttpResponse): ApiError {
        val code = problemCode(response.body)
        return when (response.status) {
            401 -> ApiError.SessionExpired
            403 -> ApiError.Forbidden(code)
            404 -> ApiError.NotFound(code)
            in 500..599 -> ApiError.Server(response.status)
            else -> ApiError.Rejected(response.status, code)
        }
    }

    private fun problemCode(body: String): String? = try {
        json.decodeFromString(ProblemBody.serializer(), body).code
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    @Serializable
    private data class ProblemBody(val code: String? = null)

    companion object {
        /** 서버가 필드를 추가해도 앱이 깨지지 않도록 알 수 없는 키는 무시한다. */
        val defaultJson = Json { ignoreUnknownKeys = true; explicitNulls = false }
    }
}
