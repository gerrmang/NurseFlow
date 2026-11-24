package com.nurseflow.policy.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.hospital.application.service.HiraHospitalClient
import com.nurseflow.policy.config.HolidayProperties
import com.nurseflow.policy.domain.CalendarDay
import com.nurseflow.policy.domain.CalendarDayKind
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Reads one month of public holidays (`isHoliday=Y`) from 한국천문연구원 특일 정보. Names containing "대체" are
 * substitute holidays. The key is sent only in the outgoing query and never logged.
 * Throws [ErrorCode.HOLIDAY_SOURCE_UNAVAILABLE] on any failure so a caller keeps what it already stored.
 */
@Component
class PublicHolidayClient(private val properties: HolidayProperties, private val mapper: JsonMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val client: RestClient by lazy {
        RestClient.builder()
            .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(properties.timeout).build()).apply {
                setReadTimeout(properties.timeout)
            })
            .build()
    }

    fun fetch(month: YearMonth): List<CalendarDay> {
        if (!properties.configured) throw BusinessException(ErrorCode.HOLIDAY_SOURCE_NOT_CONFIGURED)
        val uri = UriComponentsBuilder.fromUriString(properties.baseUri.trimEnd('/') + "/getRestDeInfo")
            .queryParam("serviceKey", "{serviceKey}")
            .queryParam("solYear", "{year}")
            .queryParam("solMonth", "{month}")
            .queryParam("numOfRows", "{rows}")
            .queryParam("_type", "json")
            .encode()
            .buildAndExpand(mapOf(
                "serviceKey" to HiraHospitalClient.decodedKey(properties.serviceKey),
                "year" to month.year.toString(), "month" to "%02d".format(month.monthValue), "rows" to 50,
            ))
            .toUri()
        val body = try {
            client.get().uri(uri).accept(MediaType.APPLICATION_JSON).retrieve().body(String::class.java).orEmpty()
        } catch (ex: RestClientResponseException) {
            log.warn("Holiday source failed: status={} reasonCode={}", ex.statusCode.value(), reasonCode(ex.responseBodyAsString))
            throw unavailable()
        } catch (ex: RestClientException) {
            log.warn("Holiday source unavailable: {}", ex.javaClass.simpleName)
            throw unavailable()
        }
        return parse(body, month)
    }

    private fun parse(body: String, month: YearMonth): List<CalendarDay> {
        reasonCode(body)?.let {
            log.warn("Holiday source rejected the request: reasonCode={}", it)
            throw unavailable()
        }
        val response = runCatching { mapper.readTree(body) }.getOrNull()?.path("response")
        if (response == null || response.isMissingNode) {
            log.warn("Holiday source returned an unreadable body")
            throw unavailable()
        }
        val resultCode = response.path("header").path("resultCode").asString("")
        if (resultCode == NO_DATA) return emptyList()
        if (resultCode != NORMAL) {
            log.warn("Holiday source returned resultCode={}", resultCode.take(10))
            throw unavailable()
        }
        // `item` is an array, a single object for one holiday, and absent (`items: ""`) for none.
        val item = response.path("body").path("items").path("item")
        val nodes = when {
            item.isArray -> item.toList()
            item.isObject -> listOf(item)
            else -> emptyList()
        }
        val days = nodes.filter { it.path("isHoliday").asString("") == "Y" }.map { node ->
            val date = runCatching { LocalDate.parse(node.path("locdate").asString(""), BASIC_DATE) }.getOrNull()
            val name = node.path("dateName").asString("").trim().take(50).ifEmpty { null }
            if (date == null || YearMonth.from(date) != month) {
                log.warn("Holiday source returned a date outside the requested month")
                throw unavailable()
            }
            CalendarDay(date, if (name?.contains("대체") == true) CalendarDayKind.SUBSTITUTE_HOLIDAY else CalendarDayKind.PUBLIC_HOLIDAY, name)
        }
        // Two names on one date (e.g. 어린이날 and 부처님오신날) are one holiday.
        return days.groupBy { it.date }.map { (_, same) -> same.first().copy(name = same.mapNotNull { it.name }.distinct().joinToString(", ").take(50).ifEmpty { null }) }
            .sortedBy { it.date }
    }

    private fun reasonCode(body: String?): String? = body?.let { REASON_CODE.find(it)?.groupValues?.get(1) }

    private fun unavailable() = BusinessException(ErrorCode.HOLIDAY_SOURCE_UNAVAILABLE)

    private companion object {
        const val NORMAL = "00"
        const val NO_DATA = "03"
        val REASON_CODE = Regex("returnReasonCode\\W{1,6}(\\d{1,3})")
        val BASIC_DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }
}
