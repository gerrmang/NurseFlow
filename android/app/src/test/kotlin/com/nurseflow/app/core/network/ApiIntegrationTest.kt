package com.nurseflow.app.core.network

import com.nurseflow.app.core.session.MemoryTokenStore
import com.nurseflow.app.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiIntegrationTest {
    private val sessionJson = """{"accessToken":"fake-access","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fake-refresh","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"demo-user","displayName":"가상 사용자"}}"""
    private val userJson = """{"userId":"demo-user","displayName":"가상 사용자","memberships":[]}"""
    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    @Test fun unauthorizedRetriesOnceWithRotatedBearerAndBodyRefresh() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json(sessionJson))
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(json(sessionJson.replace("fake-access", "fake-access-rotated")))
            server.enqueue(json(userJson))
            val base = server.url("/").toString()
            val manager = SessionManager(ApiFactory.authApi(base), MemoryTokenStore())
            manager.loginWithKakao("fake-id", "fake-nonce")
            assertEquals("demo-user", ApiFactory.nurseFlowApi(base, manager).me().userId)
            assertEquals("/api/v1/auth/kakao/session", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
            assertEquals("Bearer fake-access", server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Authorization"))
            val refresh = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("/api/v1/auth/session/refresh", refresh.path)
            assertNull(refresh.getHeader("Authorization"))
            assertTrue(refresh.body.readUtf8().contains("fake-refresh"))
            assertEquals("Bearer fake-access-rotated", server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("Authorization"))
        }
    }

    @Test fun secondUnauthorizedDoesNotLoop() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json(sessionJson))
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(json(sessionJson))
            server.enqueue(MockResponse().setResponseCode(401))
            val base = server.url("/").toString()
            val manager = SessionManager(ApiFactory.authApi(base), MemoryTokenStore())
            manager.loginWithKakao("fake-id", "fake-nonce")
            try { apiCall { ApiFactory.nurseFlowApi(base, manager).me() }; fail("Expected 401") }
            catch (e: ApiException) { assertEquals(401, e.status) }
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun wardListUsesExistingItemsContractAndPreservesRoles() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"items":[{"wardId":"demo-ward","wardName":"가상 병동","roles":["WARD_ADMIN","WARD_MEMBER"],"canViewWardCalendar":true}]}"""))
            val base = server.url("/").toString()
            val manager = SessionManager(ApiFactory.authApi(base), MemoryTokenStore())
            val wards = ApiFactory.nurseFlowApi(base, manager).wards()
            assertEquals(listOf("WARD_ADMIN", "WARD_MEMBER"), wards.items.single().roles)
            assertEquals("/api/v1/wards", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
        }
    }

    @Test fun mapsErrorsWithoutDisplayingServerDetail() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"code":"WARD_ACCESS_DENIED","detail":"untrusted server detail"}""").setResponseCode(403))
            val base = server.url("/").toString()
            val manager = SessionManager(ApiFactory.authApi(base), MemoryTokenStore())
            try { apiCall { ApiFactory.nurseFlowApi(base, manager).me() }; fail("Expected 403") }
            catch (e: ApiException) {
                assertEquals("WARD_ACCESS_DENIED", e.code)
                assertEquals("권한이 없어요.", e.message)
                assertFalse(e.message.contains("untrusted"))
            }
        }
    }

    @Test fun publishedRosterPathsQueriesAndErrorCodes() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json("""{"wardId":"demo-ward","month":"2026-11","revision":2,"timeZone":"Asia/Seoul","assignments":[{"date":"2026-11-10","shiftType":"D","startsAt":"2026-11-09T22:00:00Z","endsAt":"2026-11-10T06:00:00Z"}]}"""))
            server.enqueue(json("""{"wardId":"demo-ward","month":"2026-11","revision":2,"timeZone":"Asia/Seoul","staffing":{"required":1,"assigned":1,"unfilled":0},"slots":[{"slotId":"s","date":"2026-11-10","shiftTypeId":"t","startAt":"2026-11-09T22:00:00Z","endAt":"2026-11-10T06:00:00Z","status":"FILLED","assignment":{"nurseId":"n","displayName":"가상 간호사","locked":false}}]}"""))
            server.enqueue(json("""{"code":"PUBLISHED_ROSTER_NOT_FOUND"}""").setResponseCode(404))
            server.enqueue(json("""{"code":"WARD_CALENDAR_VIEW_REQUIRED"}""").setResponseCode(403))
            val base = server.url("/").toString()
            val api = ApiFactory.nurseFlowApi(base, SessionManager(ApiFactory.authApi(base), MemoryTokenStore()))
            assertEquals("D", api.publishedMine("demo-ward", "2026-11").assignments.single().shiftType)
            assertEquals("가상 간호사", api.publishedWard("demo-ward", "2026-11").slots.single().assignment?.displayName)
            assertEquals("/api/v1/wards/demo-ward/published-rosters/mine?month=2026-11", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
            assertEquals("/api/v1/wards/demo-ward/published-rosters?month=2026-11", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
            try { apiCall { api.publishedMine("demo-ward", "2026-12") }; fail("Expected 404") }
            catch (e: ApiException) { assertEquals(404, e.status); assertEquals("PUBLISHED_ROSTER_NOT_FOUND", e.code) }
            try { apiCall { api.publishedWard("demo-ward", "2026-12") }; fail("Expected 403") }
            catch (e: ApiException) { assertEquals(403, e.status); assertEquals("WARD_CALENDAR_VIEW_REQUIRED", e.code) }
        }
    }

    @Test fun mapsOfflineError() = runBlocking {
        try { apiCall<Unit> { throw IOException("internal transport detail") }; fail("Expected network error") }
        catch (e: ApiException) { assertEquals(0, e.status); assertEquals("NETWORK", e.code) }
    }

    @Test fun readRecoversWhenReusedConnectionClosesBeforeResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json(sessionJson))
            server.enqueue(json(userJson))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(json(userJson))
            val base = server.url("/").toString()
            val manager = SessionManager(ApiFactory.authApi(base), MemoryTokenStore())
            manager.loginWithKakao("fake-id", "fake-nonce")
            val api = ApiFactory.nurseFlowApi(base, manager)
            assertEquals("demo-user", api.me().userId)
            assertEquals("demo-user", api.me().userId)
            assertEquals(4, server.requestCount)
        }
    }

    @Test fun refreshUsesFreshConnectionAndIsNeverReplayedAfterLostResponse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(json(sessionJson))
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
            server.enqueue(json(sessionJson))
            val auth = ApiFactory.authApi(server.url("/").toString())
            auth.kakaoLogin(KakaoLoginBody("fake-id", "fake-nonce"))
            try {
                auth.refresh(RefreshTokenBody("fake-refresh"))
                fail("A lost rotation response must not be retried")
            } catch (_: IOException) { }
            assertEquals(2, server.requestCount)
            assertEquals(0, server.takeRequest(1, TimeUnit.SECONDS)!!.sequenceNumber)
            assertEquals(0, server.takeRequest(1, TimeUnit.SECONDS)!!.sequenceNumber)
        }
    }
}
