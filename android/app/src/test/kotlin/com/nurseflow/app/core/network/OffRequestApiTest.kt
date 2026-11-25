package com.nurseflow.app.core.network

import com.nurseflow.app.core.session.MemoryTokenStore
import com.nurseflow.app.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth
import java.util.concurrent.TimeUnit

class OffRequestApiTest {
    private val row = """{"requestId":"r1","nurseId":"n1","date":"2026-12-10","status":"SUBMITTED","source":"NURSE","submittedAt":"2026-11-01T00:00:00Z","decidedAt":null,"afterDeadline":false}"""
    private val session = """{"accessToken":"fictional-access","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fictional-refresh","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"fictional-user","displayName":"가상 사용자"}}"""
    private fun json(value: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(value)
    private fun withApi(block: suspend (OffRequestApi, MockWebServer, SessionManager) -> Unit) = runBlocking {
        MockWebServer().use { server ->
            val base = server.url("/").toString()
            val manager = SessionManager(ApiFactory.authApi(base), MemoryTokenStore())
            block(ApiFactory.offRequestApi(base, manager), server, manager)
        }
    }
    @Test fun listPathMonthAndRequiredFields() = withApi { api, server, _ ->
        server.enqueue(json("""{"items":[$row],"deadline":"2026-11-15T15:00:00Z","monthlyLimit":4}"""))
        val result = api.list("w1", "2026-12").checked(YearMonth.of(2026, 12))
        assertEquals("NURSE", result.items.single().source)
        assertEquals("/api/v1/wards/w1/off-requests?month=2026-12", server.takeRequest().path)
    }
    @Test fun submitOmitsNurseIdForSelfAndIncludesItForAdmin() = withApi { api, server, _ ->
        server.enqueue(json(row)); api.submit("w1", OffRequestInput("2026-12-10"))
        val own = server.takeRequest(); assertEquals("POST", own.method); assertEquals("/api/v1/wards/w1/off-requests", own.path)
        assertEquals("""{"date":"2026-12-10"}""", own.body.readUtf8())
        server.enqueue(json(row.replace("NURSE", "ADMIN"))); api.submit("w1", OffRequestInput("2026-12-10", "n1"))
        assertEquals("""{"date":"2026-12-10","nurseId":"n1"}""", server.takeRequest().body.readUtf8())
    }
    @Test fun decisionPathsHaveNoBody() = withApi { api, server, _ ->
        for (action in listOf("confirm", "reject", "withdraw")) {
            server.enqueue(json(row))
            when (action) { "confirm" -> api.confirm("w1", "r1"); "reject" -> api.reject("w1", "r1"); else -> api.withdraw("w1", "r1") }
            val request = server.takeRequest(); assertEquals("POST", request.method)
            assertEquals("/api/v1/wards/w1/off-requests/r1/$action", request.path); assertEquals(0L, request.body.size)
        }
    }
    @Test fun policyPreservesExactQuotedEtagOnSave() = withApi { api, server, _ ->
        val body = """{"monthlyLimit":0,"deadlineDay":28,"policyRevision":7}"""
        server.enqueue(json(body).setHeader("ETag", "\"policy-7\""))
        val policy = api.policy("w1").checkedPolicy(); assertEquals(0, policy.value.monthlyLimit)
        assertEquals("/api/v1/wards/w1/off-requests/policy", server.takeRequest().path)
        server.enqueue(json(body).setHeader("ETag", "\"policy-7\"")); api.savePolicy("w1", policy.etag, OffRequestPolicyInput(0, 28)).checkedPolicy()
        val save = server.takeRequest(); assertEquals("PUT", save.method); assertEquals("\"policy-7\"", save.getHeader("If-Match"))
        assertEquals("""{"monthlyLimit":0,"deadlineDay":28}""", save.body.readUtf8())
    }
    @Test fun policyMissingMismatchedEtagAndBadRangeFailClosed() = withApi { api, server, _ ->
        for (etag in listOf(null, "policy-7", "\"policy-8\"")) {
            val response = json("""{"monthlyLimit":4,"deadlineDay":15,"policyRevision":7}""")
            if (etag != null) response.setHeader("ETag", etag)
            server.enqueue(response)
            try { api.policy("w1").checkedPolicy(); fail() } catch (_: IllegalArgumentException) { }
        }
        server.enqueue(json("""{"monthlyLimit":32,"deadlineDay":15,"policyRevision":7}""").setHeader("ETag", "\"policy-7\""))
        try { api.policy("w1").checkedPolicy(); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun noContentAndMalformedJsonFailInsteadOfEmptySuccess() = withApi { api, server, _ ->
        server.enqueue(MockResponse().setResponseCode(204))
        try { api.policy("w1").checkedPolicy(); fail() } catch (_: IllegalArgumentException) { }
        server.enqueue(json("{}"))
        try { api.list("w1", "2026-12"); fail() } catch (_: kotlinx.serialization.SerializationException) { }
    }
    @Test fun unknownStatusInvalidDateAndMixedMonthAreRejected() {
        val valid = ApiJson.json.decodeFromString<OffRequest>(row)
        for (item in listOf(valid.copy(status = "UNKNOWN"), valid.copy(source = "UNKNOWN"), valid.copy(date = "2026-02-30"), valid.copy(submittedAt = "bad"))) {
            try { item.checked(); fail() } catch (_: RuntimeException) { }
        }
        try { OffRequestList(listOf(valid), "2026-11-01T00:00:00Z", 4).checked(YearMonth.of(2026, 11)); fail() } catch (_: IllegalArgumentException) { }
        assertEquals("2028-02-29", valid.copy(date = "2028-02-29").checked().date)
    }
    @Test fun eligibilityConsumesActualItemsContract() = withApi { api, server, _ ->
        server.enqueue(json("""{"demoMode":true,"policyRevision":2,"items":[{"nurseId":"n1","displayName":"가상 간호사","eligible":true,"reason":"ELIGIBLE"}]}"""))
        assertTrue(api.eligibility("w1").checked().items.single().eligible)
        assertEquals("/api/v1/wards/w1/roster-eligibility", server.takeRequest().path)
    }
    @Test fun serverErrorCodesReachCaller() = withApi { api, server, _ ->
        for ((status, code) in listOf(409 to "OFF_REQUEST_DUPLICATE", 409 to "OFF_REQUEST_LIMIT_REACHED", 409 to "OFF_REQUEST_DEADLINE_PASSED", 412 to "STALE_POLICY_REVISION", 403 to "WARD_ADMIN_REQUIRED", 404 to "NURSE_IDENTITY_REQUIRED")) {
            server.enqueue(json("""{"code":"$code"}""").setResponseCode(status))
            try { apiCall { api.list("w1", "2026-12") }; fail() } catch (e: ApiException) { assertEquals(code, e.code); assertEquals(status, e.status) }
        }
    }
    @Test fun disconnectedMutationIsNeverReplayed() = withApi { api, server, _ ->
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        try { apiCall { api.submit("w1", OffRequestInput("2026-12-10")) }; fail() } catch (e: ApiException) { assertEquals("NETWORK", e.code) }
        server.takeRequest(); assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS)); assertEquals(1, server.requestCount)
    }
    @Test fun oldApiCannotSendWithNewAccountCredentials() = withApi { old, server, manager ->
        server.enqueue(json(session)); manager.loginWithKakao("fictional-id", "fictional-nonce"); server.takeRequest()
        try { apiCall { old.submit("w1", OffRequestInput("2026-12-10")) }; fail() } catch (e: ApiException) { assertEquals("NETWORK", e.code) }
        assertNull(server.takeRequest(250, TimeUnit.MILLISECONDS))
        server.enqueue(json(row)); ApiFactory.offRequestApi(server.url("/").toString(), manager).submit("w1", OffRequestInput("2026-12-10"))
        assertEquals("Bearer fictional-access", server.takeRequest().getHeader("Authorization"))
    }
}
