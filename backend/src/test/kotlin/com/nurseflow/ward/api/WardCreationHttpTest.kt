package com.nurseflow.ward.api

import com.nurseflow.support.ApiTestBase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WardCreationHttpTest : ApiTestBase() {
    @Test
    fun `signed-in user cannot create a ward without hospital admin authorization`() {
        val (_, token) = userToken("가상 사용자")

        val response = http.post("/api/v1/wards", mapOf("name" to "가상 병동"), token)

        assertEquals(405, response.status)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wards", Int::class.java))
    }
}
