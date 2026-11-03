package com.nurseflow.app.core.network

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

data class HttpResponse(val status: Int, val body: String)

/** 요청 한 번을 실행한다. 연결·읽기 실패는 [IOException]으로 알린다. 테스트에서는 가짜 구현을 넣는다. */
fun interface HttpTransport {
    @Throws(IOException::class)
    fun execute(baseUrl: String, request: HttpRequest): HttpResponse
}

/** 추가 의존성 없이 플랫폼 [HttpURLConnection]을 사용하는 전송 계층. 호출자는 IO 스레드에서 실행한다. */
class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) : HttpTransport {
    override fun execute(baseUrl: String, request: HttpRequest): HttpResponse {
        val connection = URL(baseUrl.trimEnd('/') + request.path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            if (request.body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(request.body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val text = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            return HttpResponse(status, text)
        } finally {
            connection.disconnect()
        }
    }
}
