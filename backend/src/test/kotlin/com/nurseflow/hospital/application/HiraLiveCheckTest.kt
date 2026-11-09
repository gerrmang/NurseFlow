package com.nurseflow.hospital.application

import com.nurseflow.hospital.application.service.HiraHospitalClient
import com.nurseflow.hospital.config.HospitalProperties
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

/**
 * Manual check against the real data.go.kr service. Skipped unless `NURSEFLOW_HOSPITAL_API_KEY`
 * is set, so CI never calls the provider. Prints counts and public institution names only, never the key.
 */
class HiraLiveCheckTest {
    @Test
    fun `real provider returns hospitals for a common name`() {
        val key = System.getenv("NURSEFLOW_HOSPITAL_API_KEY").orEmpty()
        assumeTrue(key.isNotBlank(), "Set NURSEFLOW_HOSPITAL_API_KEY to run the live check")
        val client = HiraHospitalClient(HospitalProperties(HospitalProperties.Hira(serviceKey = key)), JsonMapper())

        val result = client.search("서울대학교병원", 1, 5)

        println("HIRA live check: totalCount=${result.totalCount} items=${result.items.size}")
        result.items.forEach { println("  ${it.name} | ${it.category} | ${it.address} | externalId length=${it.externalId.length}") }
        assertTrue(result.items.isNotEmpty())
    }
}
