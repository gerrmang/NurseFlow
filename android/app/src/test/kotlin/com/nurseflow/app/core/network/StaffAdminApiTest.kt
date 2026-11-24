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

class StaffAdminApiTest {
    private val staff = """{"nurseId":"n1","displayName":"가상 간호사","qualifications":[],"active":true,"linkedAccount":false,"profile":{"status":"MISSING","hospitalJoinedOn":null},"team":null,"teamOrder":null,"seniorEligible":false,"stageOverride":null,"rosterStage":null}"""
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    private fun setup(server: MockWebServer): StaffAdminApi {
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        val base = "http://127.0.0.1:${server.port}/"
        return ApiFactory.staffAdminApi(base, SessionManager(ApiFactory.authApi(base), MemoryTokenStore()))
    }
    @Test fun clearingTeamAndStageSendRequiredExplicitNulls() = runBlocking {
        MockWebServer().use { server ->
            val api = setup(server)
            server.enqueue(json(staff)); api.assignTeam("w1", "n1", AdminTeamAssignment(null))
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/api/v1/wards/w1/staff/n1/team", request.path)
            assertEquals(JsonNull, Json.parseToJsonElement(request.body.readUtf8()).jsonObject["teamId"])
            server.enqueue(json(staff)); api.settings("w1", "n1", AdminRosterSettings(false, null))
            val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
            assertEquals(setOf("seniorEligible", "stageOverride"), body.keys)
            assertEquals(JsonPrimitive(false), body["seniorEligible"]); assertEquals(JsonNull, body["stageOverride"])
        }
    }
    @Test fun membershipPatchOmitsUnrequestedPropertiesAndPreservesFalse() = runBlocking {
        MockWebServer().use { server ->
            val api = setup(server)
            server.enqueue(json("""{"userId":"u1","displayName":"가상 관리자","roles":["WARD_ADMIN"],"active":false,"nurseId":null}"""))
            api.membership("w1", "u1", AdminMembershipInput(active = false))
            val req = server.takeRequest()
            assertEquals("PATCH", req.method); assertEquals("/api/v1/wards/w1/memberships/u1", req.path)
            assertEquals("""{"active":false}""", req.body.readUtf8())
        }
    }
    @Test fun inactiveStaffListUsesContractAndIgnoresAdditionalFields() = runBlocking {
        MockWebServer().use { server ->
            val api = setup(server)
            server.enqueue(json("""{"items":[$staff],"page":1,"totalItems":1}"""))
            val row = api.staff("w1").items.single()
            assertNull(row.profile.hospitalJoinedOn); assertNull(row.stageOverride)
            assertEquals("/api/v1/wards/w1/staff?includeInactive=true", server.takeRequest().path)
        }
    }
    @Test fun teamOrderUsesTeamPathAndCompleteArray() = runBlocking {
        MockWebServer().use { server ->
            val api = setup(server); server.enqueue(json("""{"items":[$staff]}"""))
            api.order("w1", "t1", AdminTeamOrder(listOf("n2", "n1")))
            val req = server.takeRequest()
            assertEquals("/api/v1/wards/w1/teams/t1/order", req.path)
            assertEquals("""{"nurseIds":["n2","n1"]}""", req.body.readUtf8())
        }
    }
    @Test fun droppedMutationResponseDoesNotReplay() = runBlocking {
        MockWebServer().use { server ->
            val api = setup(server)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            try { apiCall { api.createTeam("w1", AdminTeamInput("가상팀", 1)) }; fail("network failure expected") }
            catch (e: ApiException) { assertEquals("NETWORK", e.code) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)); assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
        }
    }
    @Test fun serverConflictKeepsStableErrorCode() = runBlocking {
        MockWebServer().use { server ->
            val api = setup(server)
            server.enqueue(MockResponse().setResponseCode(409).setHeader("Content-Type", "application/problem+json").setBody("""{"status":409,"code":"LAST_WARD_ADMIN","detail":"고정"}"""))
            try { apiCall { api.membership("w1", "u1", AdminMembershipInput(active = false)) }; fail("conflict expected") }
            catch (e: ApiException) { assertEquals("LAST_WARD_ADMIN", e.code) }
        }
    }
    @Test fun refreshIntoAnotherAccountNeverReplaysStaffMutation() = runBlocking {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            val base = "http://127.0.0.1:${server.port}/"
            fun session(user: String) = """{"accessToken":"fictional-access-$user","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fictional-refresh-$user","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"$user","displayName":"가상 사용자"}}"""
            server.enqueue(json(session("u1")))
            val manager = SessionManager(ApiFactory.authApi(base), MemoryTokenStore())
            manager.loginWithKakao("fictional-id", "fictional-nonce")
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(json(session("u2")))
            try { apiCall { ApiFactory.staffAdminApi(base, manager).createTeam("w1", AdminTeamInput("가상팀", 1)) }; fail("401 expected") }
            catch (e: ApiException) { assertEquals(401, e.status) }
            assertEquals(3, server.requestCount)
            assertEquals("/api/v1/auth/kakao/session", server.takeRequest().path)
            assertEquals("/api/v1/wards/w1/teams", server.takeRequest().path)
            assertEquals("/api/v1/auth/session/refresh", server.takeRequest().path)
            assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
        }
    }
    @Test fun oldStaffClientCannotSendWithNewAccountCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.start(java.net.InetAddress.getByName("127.0.0.1"), 0)
            val base = "http://127.0.0.1:${server.port}/"
            fun session(user: String) = """{"accessToken":"fictional-access-$user","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fictional-refresh-$user","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"$user","displayName":"가상 사용자"}}"""
            val manager = SessionManager(ApiFactory.authApi(base), MemoryTokenStore())
            server.enqueue(json(session("u1"))); manager.loginWithKakao("fictional-id", "fictional-nonce")
            val old = ApiFactory.staffAdminApi(base, manager)
            server.enqueue(json(session("u2"))); manager.loginWithKakao("fictional-id", "fictional-nonce")
            try { apiCall { old.createTeam("w1", AdminTeamInput("가상팀", 1)) }; fail("session change expected") }
            catch (e: ApiException) { assertEquals("NETWORK", e.code) }
            assertEquals(2, server.requestCount)
        }
    }
}
