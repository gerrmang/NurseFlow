package com.nurseflow.hospital.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.hospital.application.model.HospitalCandidate
import com.nurseflow.hospital.application.model.HospitalSearchResult
import com.nurseflow.hospital.config.HospitalProperties
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.net.URLDecoder

/**
 * Calls 건강보험심사평가원 병원정보서비스 (`getHospBasisList`) and keeps only the fields a ward
 * needs. The service key is sent only in the outgoing query and is never logged: failures log
 * the HTTP status or data.go.kr reason code, never the request URI.
 */
@Component
class HiraHospitalClient(private val properties: HospitalProperties, private val mapper: JsonMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient by lazy {
        val timeout = properties.hira.timeout
        RestClient.builder()
            .requestFactory(SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(timeout)
                setReadTimeout(timeout)
            })
            .build()
    }

    fun search(name: String, page: Int, size: Int): HospitalSearchResult {
        val hira = properties.hira
        val uri = UriComponentsBuilder.fromUriString(hira.baseUri.trimEnd('/') + "/getHospBasisList")
            .queryParam("serviceKey", "{serviceKey}")
            .queryParam("yadmNm", "{name}")
            .queryParam("pageNo", "{page}")
            .queryParam("numOfRows", "{size}")
            .queryParam("_type", "json")
            .encode()
            .buildAndExpand(mapOf("serviceKey" to decodedKey(hira.serviceKey), "name" to name, "page" to page, "size" to size))
            .toUri()
        val body = try {
            client.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve().body(String::class.java)
        } catch (ex: RestClientResponseException) {
            log.warn("HIRA hospital search failed: status={}", ex.statusCode.value())
            throw unavailable()
        } catch (ex: RestClientException) {
            log.warn("HIRA hospital search unavailable: {}", ex.javaClass.simpleName)
            throw unavailable()
        }
        return parse(body.orEmpty(), page, size)
    }

    private fun parse(body: String, page: Int, size: Int): HospitalSearchResult {
        if (body.trimStart().startsWith("<")) {
            // data.go.kr gateway errors (unregistered key, quota exceeded, ...) arrive as XML even for _type=json.
            log.warn("HIRA hospital search rejected: reasonCode={}", REASON_CODE.find(body)?.groupValues?.get(1))
            throw unavailable()
        }
        val response = runCatching { mapper.readTree(body) }.getOrNull()?.path("response") ?: run {
            log.warn("HIRA hospital search returned an unreadable body")
            throw unavailable()
        }
        val resultCode = response.path("header").path("resultCode").asString("")
        if (resultCode == NO_DATA) return result(emptyList(), page, size, 0)
        if (resultCode != NORMAL) {
            log.warn("HIRA hospital search returned resultCode={}", resultCode.take(10))
            throw unavailable()
        }
        val content = response.path("body")
        // `item` is an array, a single object when there is one hit, and absent (`items: ""`) when there is none.
        val item = content.path("items").path("item")
        val nodes = when {
            item.isArray -> item.toList()
            item.isObject -> listOf(item)
            else -> emptyList()
        }
        return result(nodes.mapNotNull(::candidate), page, size, content.path("totalCount").asInt(0))
    }

    private fun candidate(node: JsonNode): HospitalCandidate? {
        fun text(field: String) = node.path(field).asString("").trim().takeIf { it.isNotEmpty() }
        return HospitalCandidate(
            source = SOURCE,
            externalId = text("ykiho") ?: return null,
            name = text("yadmNm") ?: return null,
            address = text("addr") ?: "",
            category = text("clCdNm"),
        )
    }

    private fun result(items: List<HospitalCandidate>, page: Int, size: Int, total: Int) =
        HospitalSearchResult(items, page, size, maxOf(total, items.size), ATTRIBUTION)

    private fun unavailable() = BusinessException(ErrorCode.HOSPITAL_SEARCH_UNAVAILABLE)

    companion object {
        const val SOURCE = "HIRA"
        const val ATTRIBUTION = "건강보험심사평가원 병원정보서비스"
        private const val NORMAL = "00"
        private const val NO_DATA = "03"
        private val REASON_CODE = Regex("<returnReasonCode>\\s*(\\d{1,3})\\s*</returnReasonCode>")
        private val PERCENT_ESCAPE = Regex("%[0-9A-Fa-f]{2}")

        /** data.go.kr issues each key in a "Decoding" and a URL-encoded "Encoding" form; send the decoded one. */
        internal fun decodedKey(key: String): String = key.trim().let {
            if (PERCENT_ESCAPE.containsMatchIn(it)) URLDecoder.decode(it, Charsets.UTF_8) else it
        }
    }
}
