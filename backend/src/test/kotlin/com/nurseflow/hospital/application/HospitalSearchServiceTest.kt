package com.nurseflow.hospital.application

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.hospital.application.service.HiraHospitalClient
import com.nurseflow.hospital.application.service.HospitalSearchService
import com.nurseflow.hospital.config.HospitalProperties
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tools.jackson.databind.json.JsonMapper

class HospitalSearchServiceTest {
    @Test
    fun `search without a service key is not configured`() {
        val properties = HospitalProperties(HospitalProperties.Hira(serviceKey = " "))
        val service = HospitalSearchService(properties, HiraHospitalClient(properties, JsonMapper()))

        val ex = assertThrows<BusinessException> { service.search("가상병원", 1, 10) }
        assertEquals(ErrorCode.HOSPITAL_SEARCH_NOT_CONFIGURED, ex.errorCode)
    }

    @Test
    fun `both data go kr key forms are sent decoded`() {
        assertEquals("abc+def/ghi==", HiraHospitalClient.decodedKey("abc+def/ghi=="))
        assertEquals("abc+def/ghi==", HiraHospitalClient.decodedKey(" abc%2Bdef%2Fghi%3D%3D "))
    }
}
