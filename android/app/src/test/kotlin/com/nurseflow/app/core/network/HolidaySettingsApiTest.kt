package com.nurseflow.app.core.network

import com.nurseflow.app.core.session.MemoryTokenStore
import com.nurseflow.app.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

class HolidaySettingsApiTest {
    private val policy = """{"dailyStandardMinutes":480,"leaveCreditMinutes":480,"halfDayCreditMinutes":240,"sofCreditMinutes":480,"nightDedicatedTargetMinutes":null,"nightDedicatedNightCreditMinutes":null,"policyRevision":2}"""
    private fun api(s: MockWebServer): HolidaySettingsApi {
        s.start(InetAddress.getByName("127.0.0.1"), 0); val base = s.url("/").toString()
        return ApiFactory.holidaySettingsApi(base, SessionManager(ApiFactory.authApi(base), MemoryTokenStore()))
    }
    @Test fun policySendsSharedTagAndZeroWithoutRevisionAndDecodesNull() = runBlocking {
        MockWebServer().use { s ->
            val api = api(s); s.enqueue(MockResponse().setHeader("ETag", "\"p2\"").setBody(policy))
            val result = api.savePolicy("w1", "\"p1\"", HolidayPolicyInput(480, 480, 240, 480, 0, null))
            assertNull(result.body()!!.nightDedicatedNightCreditMinutes)
            val r = s.takeRequest(); assertEquals("PUT", r.method); assertEquals("/api/v1/wards/w1/target-hour-policy", r.path); assertEquals("\"p1\"", r.getHeader("If-Match"))
            val body = Json.parseToJsonElement(r.body.readUtf8()).jsonObject
            assertEquals(JsonPrimitive(0), body["nightDedicatedTargetMinutes"]); assertFalse(body.containsKey("policyRevision"))
        }
    }
    @Test fun wholeCalendarReplacementIncludesEmptyListAndRefreshHasNoTag() = runBlocking {
        MockWebServer().use { s ->
            val api = api(s); s.enqueue(MockResponse().setResponseCode(412))
            api.saveCalendar("w1", "2026-11", "\"p1\"", HolidayCalendarInput(emptyList()))
            val r = s.takeRequest(); assertEquals("/api/v1/wards/w1/holiday-calendar?month=2026-11", r.path); assertEquals("{\"days\":[]}", r.body.readUtf8())
            s.enqueue(MockResponse().setResponseCode(503)); api.refresh("w1", "2026-11")
            val refresh = s.takeRequest(); assertEquals("POST", refresh.method); assertNull(refresh.getHeader("If-Match")); assertEquals("/api/v1/wards/w1/holiday-calendar/refresh?month=2026-11", refresh.path)
        }
    }
    @Test fun lostResponseIsNeverAutomaticallyReplayed() = runBlocking {
        MockWebServer().use { s ->
            val api = api(s); s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            try { apiCall { api.saveCalendar("w1", "2026-11", "\"p1\"", HolidayCalendarInput(emptyList())) }; fail("Expected failure") } catch (e: ApiException) { assertEquals("NETWORK", e.code) }
            assertNotNull(s.takeRequest(2, TimeUnit.SECONDS)); assertNull(s.takeRequest(200, TimeUnit.MILLISECONDS))
        }
    }
}
