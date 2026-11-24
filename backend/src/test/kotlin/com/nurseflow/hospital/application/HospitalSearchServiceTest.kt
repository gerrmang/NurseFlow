package com.nurseflow.hospital.application

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.hospital.application.service.HiraHospitalClient
import com.nurseflow.hospital.application.service.HospitalSearchService
import com.nurseflow.hospital.config.HospitalProperties
import com.nurseflow.hospital.persistence.repository.HospitalJdbcRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.jdbc.core.JdbcTemplate
import tools.jackson.databind.json.JsonMapper

class HospitalSearchServiceTest {
    @Test
    fun `search without a service key is not configured`() {
        val properties = HospitalProperties(HospitalProperties.Hira(serviceKey = " "))
        val noDataset = object : HospitalJdbcRepository(JdbcTemplate()) {
            override fun latestImport(source: String) = null
        }
        val service = HospitalSearchService(properties, noDataset, HiraHospitalClient(properties, JsonMapper()))

        val ex = assertThrows<BusinessException> { service.search("가상병원", 1, 10) }
        assertEquals(ErrorCode.HOSPITAL_SEARCH_NOT_CONFIGURED, ex.errorCode)
    }

    @Test
    fun `attribution names the dataset period`() {
        assertEquals("건강보험심사평가원 전국 병의원 및 약국 현황 (2026.9. 기준)", HospitalSearchService.attribution("2026-09"))
        assertEquals("건강보험심사평가원 전국 병의원 및 약국 현황", HospitalSearchService.attribution("unknown"))
    }

    @Test
    fun `both data go kr key forms are sent decoded`() {
        assertEquals("abc+def/ghi==", HiraHospitalClient.decodedKey("abc+def/ghi=="))
        assertEquals("abc+def/ghi==", HiraHospitalClient.decodedKey(" abc%2Bdef%2Fghi%3D%3D "))
    }

    @Test
    fun `an unregistered key is unavailable, not a crash`() {
        val properties = HospitalProperties(
            HospitalProperties.Hira(baseUri = com.nurseflow.support.FakeHiraServer.baseUri, serviceKey = "unregistered-key"),
        )
        val ex = assertThrows<BusinessException> { HiraHospitalClient(properties, JsonMapper()).search("가상병원", 1, 10) }
        assertEquals(ErrorCode.HOSPITAL_SEARCH_UNAVAILABLE, ex.errorCode)
    }
}
