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
}
