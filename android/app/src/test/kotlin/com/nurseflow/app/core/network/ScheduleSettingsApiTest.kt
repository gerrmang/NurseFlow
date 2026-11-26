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

class ScheduleSettingsApiTest {
    private val shift = """{"shiftTypeId":"s1","code":"N","displayName":"가상 Night","kind":"NIGHT","startTime":"22:00","endTime":"07:00","spansMidnight":true,"breakMinutes":30,"recognizedMinutes":510,"targetCreditMinutes":0,"durationMinutes":540,"active":false}"""
    private val input = ScheduleShiftInput("N", "가상 Night", "NIGHT", "22:00", "07:00", true, 30, 510, 0, false)
    private fun json(body: String, etag: String = "\"8\"") = MockResponse().setHeader("Content-Type", "application/json").setHeader("ETag", etag).setBody(body)
    private fun api(server: MockWebServer): ScheduleSettingsApi {
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        val base = server.url("/").toString()
        return ApiFactory.scheduleSettingsApi(base, SessionManager(ApiFactory.authApi(base), MemoryTokenStore()))
    }
    @Test fun shiftWritesCarryPolicyTagAndCreationKeyAndExplicitFalseZero() = runBlocking {
        MockWebServer().use { s ->
            val api = api(s); s.enqueue(json(shift))
            val response = api.createShift("w1", "\"7\"", "fictional-key", input)
            assertEquals("\"8\"", response.headers()["ETag"])
            val r = s.takeRequest(); assertEquals("/api/v1/wards/w1/shift-types", r.path)
            assertEquals("POST", r.method); assertEquals("\"7\"", r.getHeader("If-Match")); assertEquals("fictional-key", r.getHeader("Idempotency-Key"))
            val body = Json.parseToJsonElement(r.body.readUtf8()).jsonObject
            assertEquals(JsonPrimitive(false), body["active"]); assertEquals(JsonPrimitive(0), body["targetCreditMinutes"])
            s.enqueue(json(shift)); api.updateShift("w1", "s1", "\"8\"", input)
            assertEquals("PUT", s.takeRequest().method)
        }
    }
    @Test fun inactiveListAndPreviewUseContractPaths() = runBlocking {
        MockWebServer().use { s ->
            val api = api(s); s.enqueue(json("""{"items":[$shift],"policyRevision":8}"""))
            assertFalse(api.shifts("w1").body()!!.items.single().active)
            assertEquals("/api/v1/wards/w1/shift-types?includeInactive=true", s.takeRequest().path)
            s.enqueue(json("""{"shiftType":$shift,"policyRevision":8,"flagsDrafts":true,"rewritesDraftSlotTimes":false,"affectedDrafts":[],"publishedRostersUsingType":1}"""))
            assertEquals(1, api.preview("w1", "s1", input).body()!!.publishedRostersUsingType)
            assertEquals("/api/v1/wards/w1/shift-types/s1/preview", s.takeRequest().path)
        }
    }
    @Test fun requirementsReplaceWholeListAndPreserveQualificationsAndTeam() = runBlocking {
        MockWebServer().use { s ->
            val api = api(s); s.enqueue(MockResponse().setResponseCode(412))
            api.requirements("r1", "\"6\"", ScheduleRequirements(listOf(ScheduleRequirement("2026-11-05", "s1", 2, listOf("Q1"), "t1"))))
            val req = s.takeRequest(); assertEquals("/api/v1/rosters/r1/requirements", req.path)
            assertEquals("\"6\"", req.getHeader("If-Match"))
            val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject["items"]!!.jsonArray.single().jsonObject
            assertEquals(JsonPrimitive("t1"), body["teamId"]); assertEquals(JsonArray(listOf(JsonPrimitive("Q1"))), body["requiredQualifications"])
        }
    }
    @Test fun absenceRoundTripPreservesOriginalInstantStringsAndDelete204() = runBlocking {
        MockWebServer().use { s ->
            val api = api(s)
            val a = ScheduleAbsenceInput("n1", "APPROVED_HALF_DAY", "2026-11-05T09:00:30.123456789+09:00", "2026-11-05T04:00:00.000000001Z")
            s.enqueue(json("""{"absenceId":"a1","nurseId":"n1","kind":"APPROVED_HALF_DAY","startsAt":"${a.startsAt}","endsAt":"${a.endsAt}"}"""))
            assertEquals(a.startsAt, api.updateAbsence("r1", "a1", "\"3\"", a).body()!!.startsAt)
            val req = s.takeRequest(); assertEquals("/api/v1/rosters/r1/absences/a1", req.path)
            assertEquals(a.startsAt, Json.parseToJsonElement(req.body.readUtf8()).jsonObject["startsAt"]!!.jsonPrimitive.content)
            s.enqueue(MockResponse().setResponseCode(204)); assertEquals(204, api.deleteAbsence("r1", "a1", "\"4\"").code())
            assertEquals("DELETE", s.takeRequest().method)
        }
    }
    @Test fun missingSuccessfulBodyIsExposedToStateValidation() = runBlocking {
        MockWebServer().use { s -> val api = api(s); s.enqueue(MockResponse().setResponseCode(204)); assertNull(api.shifts("w1").body()) }
    }
    @Test fun droppedWriteResponseIsNotAutomaticallyReplayed() = runBlocking {
        MockWebServer().use { s ->
            val api = api(s); s.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            try { apiCall { api.createShift("w1", "\"1\"", "fictional-key", input) }; fail("Expected network failure") }
            catch (e: ApiException) { assertEquals("NETWORK", e.code) }
            assertNotNull(s.takeRequest(2, TimeUnit.SECONDS)); assertNull(s.takeRequest(200, TimeUnit.MILLISECONDS))
        }
    }
}
