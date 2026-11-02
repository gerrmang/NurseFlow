package com.nurseflow.support

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/** Small JSON client for tests that must pass through the real servlet and security filter chain. */
class HttpTestClient(private val port: Int, private val mapper: JsonMapper) {
    private val client = HttpClient.newHttpClient()

    data class Response(val status: Int, val headers: Map<String, List<String>>, val body: String, val json: JsonNode?) {
        fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
        val code: String? get() = json?.get("code")?.asString()
        val fieldNames: List<String>
            get() = json?.get("fieldErrors")?.values()?.map { it.get("field").asString() }.orEmpty()
    }

    fun get(path: String, token: String? = null, headers: Map<String, String> = emptyMap()) =
        send("GET", path, null, token, headers)

    fun post(path: String, body: Any?, token: String? = null, headers: Map<String, String> = emptyMap()) =
        send("POST", path, body, token, headers)

    fun put(path: String, body: Any?, token: String? = null, headers: Map<String, String> = emptyMap()) =
        send("PUT", path, body, token, headers)

    fun delete(path: String, token: String? = null, headers: Map<String, String> = emptyMap()) =
        send("DELETE", path, null, token, headers)

    fun send(method: String, path: String, body: Any?, token: String?, headers: Map<String, String>): Response {
        val payload = when (body) {
            null -> HttpRequest.BodyPublishers.noBody()
            is String -> HttpRequest.BodyPublishers.ofString(body)
            else -> HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))
        }
        val builder = HttpRequest.newBuilder(URI.create("http://localhost:$port$path"))
            .method(method, payload)
            .header("Accept", "application/json, application/problem+json")
        if (body != null) builder.header("Content-Type", "application/json")
        token?.let { builder.header("Authorization", "Bearer $it") }
        headers.forEach { (k, v) -> builder.header(k, v) }
        val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
        val json = response.body().takeIf { it.isNotBlank() }?.let { runCatching { mapper.readTree(it) }.getOrNull() }
        return Response(response.statusCode(), response.headers().map(), response.body(), json)
    }
}
