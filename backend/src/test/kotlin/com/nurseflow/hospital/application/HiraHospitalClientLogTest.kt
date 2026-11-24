package com.nurseflow.hospital.application

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.hospital.application.service.HiraHospitalClient
import com.nurseflow.hospital.config.HospitalProperties
import com.nurseflow.support.FakeHiraServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper

/** data.go.kr gateway rejections reach the log as status + reason code, including 401 bodies. */
class HiraHospitalClientLogTest {
    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(HiraHospitalClient::class.java) as Logger

    @BeforeEach
    fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detach() {
        logger.detachAppender(appender)
    }

    private fun search(key: String) = HiraHospitalClient(
        HospitalProperties(HospitalProperties.Hira(baseUri = FakeHiraServer.baseUri, serviceKey = key)), JsonMapper(),
    ).search("가상병원", 1, 10)

    @Test
    fun `a 401 for a missing key keeps its reason code`() {
        assertEquals(ErrorCode.HOSPITAL_SEARCH_UNAVAILABLE, assertThrows<BusinessException> { search(" ") }.errorCode)
        assertEquals("HIRA hospital search failed: status=401 reasonCode=20", appender.list.single().formattedMessage)
    }

    @Test
    fun `a 403 for an unregistered key keeps its reason code`() {
        assertEquals(ErrorCode.HOSPITAL_SEARCH_UNAVAILABLE, assertThrows<BusinessException> { search("unregistered-key") }.errorCode)
        assertEquals("HIRA hospital search failed: status=403 reasonCode=30", appender.list.single().formattedMessage)
    }
}
