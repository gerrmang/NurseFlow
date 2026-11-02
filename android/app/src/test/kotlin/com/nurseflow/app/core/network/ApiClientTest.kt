package com.nurseflow.app.core.network

import com.nurseflow.app.feature.organization.data.RemoteOrganizationRepository
import com.nurseflow.app.feature.organization.domain.HospitalWard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ApiClientTest {
    private val requests = mutableListOf<HttpRequest>()

    private fun repository(
        token: String? = "token-1",
        baseUrl: String = "http://server.test",
        respond: (HttpRequest) -> HttpResponse,
    ) = RemoteOrganizationRepository(
        ApiClient(baseUrl, { token }, HttpTransport { _, request -> requests += request; respond(request) }),
        Dispatchers.Unconfined,
    )

    @Test
    fun `my wards parses the server contract including legacy wards without hospital`() = runTest {
        val repo = repository {
            HttpResponse(200, """{"items":[
              {"wardId":"w1","wardName":"내과 병동","timeZone":"Asia/Seoul","hospitalId":"h1","hospitalName":"가상 병원","roles":["WARD_ADMIN"],"canViewWardCalendar":true},
              {"wardId":"w2","wardName":"기존 병동","timeZone":"Asia/Seoul","hospitalId":null,"hospitalName":null,"roles":["WARD_MEMBER"],"canViewWardCalendar":false,"futureField":1}
            ]}""")
        }

        val wards = (repo.myWards() as ApiResult.Success).value

        assertEquals(listOf("w1", "w2"), wards.map { it.wardId })
        assertTrue(wards[0].isWardAdmin)
        assertNull(wards[1].hospitalId)
        assertEquals("GET", requests.single().method)
        assertEquals("/api/v1/wards", requests.single().path)
        assertEquals("Bearer token-1", requests.single().headers["Authorization"])
    }

    @Test
    fun `create ward sends only name and time zone with the idempotency key`() = runTest {
        val repo = repository {
            HttpResponse(201, """{"wardId":"w9","hospitalId":"h1","name":"신규 병동","timeZone":"Asia/Seoul","createdAt":"2026-11-02T00:00:00Z"}""")
        }

        val created = repo.createWard("h1", "신규 병동", null, "key-123")

        assertEquals(ApiResult.Success(HospitalWard("w9", "h1", "신규 병동", "Asia/Seoul")), created)
        val request = requests.single()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/hospitals/h1/wards", request.path)
        assertEquals("key-123", request.headers["Idempotency-Key"])
        assertEquals("""{"name":"신규 병동"}""", request.body)
        assertFalse("역할 필드를 보내지 않는다", request.body!!.contains("role", ignoreCase = true))
    }

    @Test
    fun `server problem responses map to stable error categories`() = runTest {
        val cases = mapOf(
            401 to ApiError.SessionExpired,
            403 to ApiError.Forbidden("HOSPITAL_ADMIN_REQUIRED"),
            404 to ApiError.NotFound("HOSPITAL_ADMIN_REQUIRED"),
            409 to ApiError.Rejected(409, "HOSPITAL_ADMIN_REQUIRED"),
            503 to ApiError.Server(503),
        )
        cases.forEach { (status, expected) ->
            val repo = repository { HttpResponse(status, """{"type":"about:blank","status":$status,"code":"HOSPITAL_ADMIN_REQUIRED","detail":"x"}""") }
            assertEquals("status $status", ApiResult.Failure(expected), repo.myHospitals())
        }
        val noBody = repository { HttpResponse(400, "") }
        assertEquals(ApiResult.Failure(ApiError.Rejected(400, null)), noBody.myHospitals())
    }

    @Test
    fun `transport and configuration failures never reach the server or crash`() = runTest {
        assertEquals(ApiResult.Failure(ApiError.Network), repository { throw IOException("offline") }.myWards())
        assertEquals(ApiResult.Failure(ApiError.UnexpectedResponse), repository { HttpResponse(200, "<html>") }.myWards())

        requests.clear()
        assertEquals(ApiResult.Failure(ApiError.SignedOut), repository(token = null) { error("호출되면 안 됨") }.myWards())
        assertEquals(ApiResult.Failure(ApiError.SignedOut), repository(token = " ") { error("호출되면 안 됨") }.myWards())
        assertEquals(ApiResult.Failure(ApiError.NotConfigured), repository(baseUrl = "") { error("호출되면 안 됨") }.myWards())
        assertTrue(requests.isEmpty())
        assertTrue(ApiError.Network.outcomeUnknown && ApiError.Server(500).outcomeUnknown)
        assertFalse(ApiError.Rejected(409, "WARD_NAME_TAKEN").outcomeUnknown)
    }
}
