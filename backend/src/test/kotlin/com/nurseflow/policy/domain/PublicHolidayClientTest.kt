package com.nurseflow.policy.domain

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.policy.application.service.PublicHolidayClient
import com.nurseflow.policy.config.HolidayProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper
import java.time.YearMonth

class PublicHolidayClientTest {
    @Test
    fun `without a key nothing is called and the admin is told to enter holidays`() {
        val client = PublicHolidayClient(HolidayProperties(serviceKey = " "), JsonMapper.builder().build())
        val error = assertThrows<BusinessException> { client.fetch(YearMonth.of(2026, 9)) }
        assertEquals(ErrorCode.HOLIDAY_SOURCE_NOT_CONFIGURED, error.errorCode)
    }

    private val client = PublicHolidayClient(HolidayProperties(serviceKey = "fake"), JsonMapper.builder().build())

    private fun header(body: String) = """{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},$body}}"""

    @Test
    fun `an empty month needs a stated totalCount of 0`() {
        val month = YearMonth.of(2026, 11)
        assertEquals(emptyList<Any>(), client.parse(header(""""body":{"items":"","totalCount":0}"""), month))
    }

    @Test
    fun `a body missing its items or count is a source failure, so the stored holidays stay`() {
        val month = YearMonth.of(2026, 11)
        listOf(
            header(""""body":{"totalCount":1}"""),
            header(""""body":{"items":""}"""),
            header(""""other":{}"""),
            header(""""body":{"items":{"item":"x"},"totalCount":1}"""),
            header(""""body":{"items":{"item":[{"isHoliday":"Y","locdate":20261103}]},"totalCount":2}"""),
        ).forEach { body ->
            val error = assertThrows<BusinessException> { client.parse(body, month) }
            assertEquals(ErrorCode.HOLIDAY_SOURCE_UNAVAILABLE, error.errorCode, body)
        }
    }
}
