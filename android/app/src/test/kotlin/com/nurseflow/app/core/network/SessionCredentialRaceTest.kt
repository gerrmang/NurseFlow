package com.nurseflow.app.core.network

import com.nurseflow.app.core.session.MemoryTokenStore
import com.nurseflow.app.core.session.SessionCredentials
import com.nurseflow.app.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class SessionCredentialRaceTest {
    private class FakeAuth : AuthApi {
        var user = "u1"
        override suspend fun kakaoLogin(body: KakaoLoginBody) = session(user, "initial")
        override suspend fun refresh(body: RefreshTokenBody) = session(user, "rotated")
        override suspend fun logout(body: RefreshTokenBody) = Unit
        private fun session(user: String, version: String) = SessionDto(
            "fictional-access-$user-$version", "2099-01-01T00:00:00Z",
            "fictional-refresh-$user-$version", "2099-02-01T00:00:00Z",
            SessionUserDto(user, "가상 사용자"),
        )
    }

    // Freeze one snapshot read, then let the test complete a new login on another thread.
    private class SnapshotGate(
        private val manager: SessionManager,
        private val pauseAt: Int,
        private val captureBeforeSwitch: Boolean,
    ) {
        val reached = CountDownLatch(1)
        val resume = CountDownLatch(1)
        private val reads = AtomicInteger()
        fun read(): SessionCredentials? {
            if (reads.incrementAndGet() != pauseAt) return manager.credentials
            val captured = if (captureBeforeSwitch) manager.credentials else null
            reached.countDown()
            check(resume.await(5, TimeUnit.SECONDS)) { "Snapshot gate timed out" }
            return if (captureBeforeSwitch) captured else manager.credentials
        }
    }

    @Test fun accountSwitchAfterBearerSnapshotNeverUsesNewAccountToken() = bearerRace(true)
    @Test fun accountSwitchBeforeBearerSnapshotBlocksOldRequest() = bearerRace(false)
    @Test fun accountSwitchAfterRefreshSnapshotNeverUsesNewAccountToken() = refreshRace(true)
    @Test fun accountSwitchBeforeRefreshSnapshotBlocksReplay() = refreshRace(false)

    @Test fun snapshotsRemainConsistentAcrossLoginRefreshAndLogout() = runBlocking {
        val auth = FakeAuth()
        val manager = SessionManager(auth, MemoryTokenStore())
        assertNull(manager.credentials)
        manager.loginWithKakao("fictional-id", "fictional-nonce")
        val first = manager.credentials!!
        auth.user = "u2"; manager.loginWithKakao("fictional-id", "fictional-nonce")
        val second = manager.credentials!!
        assertEquals("u1", first.identity.userId)
        assertEquals("fictional-access-u1-initial", first.accessToken)
        assertEquals("u2", second.identity.userId)
        assertNotEquals(first.identity.generation, second.identity.generation)
        assertEquals(manager.state.value, second.identity)
        manager.refreshAfterUnauthorized(second.accessToken)
        val refreshed = manager.credentials!!
        assertEquals(second.identity, refreshed.identity)
        assertEquals("fictional-access-u2-rotated", refreshed.accessToken)
        assertEquals("fictional-access-u2-initial", second.accessToken)
        manager.logout()
        assertNull(manager.credentials)
        assertNull(manager.accessToken)
    }

    private fun bearerRace(captureBeforeSwitch: Boolean) = runBlocking {
        val auth = FakeAuth()
        val manager = SessionManager(auth, MemoryTokenStore())
        manager.loginWithKakao("fictional-id", "fictional-nonce")
        val gate = SnapshotGate(manager, 1, captureBeforeSwitch)
        val worker = Executors.newSingleThreadExecutor()
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            server.enqueue(MockResponse().setBody("{}"))
            val client = OkHttpClient.Builder().addInterceptor(BearerInterceptor(gate::read)).build()
            val request = Request.Builder().url(server.url("/api/v1/wards/w1/teams"))
                .tag(StaffSessionIdentity::class.java, StaffSessionIdentity("u1", manager.credentials!!.identity.generation))
                .post("{}".toRequestBody()).build()
            try {
                val sent = worker.submit<Boolean> {
                    try { client.newCall(request).execute().use { it.isSuccessful } }
                    catch (_: java.io.IOException) { false }
                }
                assertTrue(gate.reached.await(5, TimeUnit.SECONDS))
                auth.user = "u2"; manager.loginWithKakao("fictional-id", "fictional-nonce")
                gate.resume.countDown()
                assertEquals(captureBeforeSwitch, sent.get(5, TimeUnit.SECONDS))
                if (captureBeforeSwitch) {
                    val received = server.takeRequest(2, TimeUnit.SECONDS)!!
                    assertEquals("Bearer fictional-access-u1-initial", received.getHeader("Authorization"))
                } else assertEquals(0, server.requestCount)
            } finally {
                gate.resume.countDown(); worker.shutdownNow()
                client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow()
            }
        }
    }

    private fun refreshRace(captureBeforeSwitch: Boolean) = runBlocking {
        val auth = FakeAuth()
        val manager = SessionManager(auth, MemoryTokenStore())
        manager.loginWithKakao("fictional-id", "fictional-nonce")
        val original = manager.credentials!!
        val request = Request.Builder().url("http://127.0.0.1/api/v1/wards/w1/teams")
            .tag(StaffSessionIdentity::class.java, StaffSessionIdentity("u1", original.identity.generation))
            .header("Authorization", "Bearer ${original.accessToken}").build()
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(401).message("Unauthorized").build()
        // The first read checks the response owner; the second chooses the refreshed token.
        val gate = SnapshotGate(manager, 2, captureBeforeSwitch)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val replay = worker.submit<Request?> { RefreshAuthenticator(manager, gate::read).authenticate(null, response) }
            assertTrue(gate.reached.await(5, TimeUnit.SECONDS))
            auth.user = "u2"; manager.loginWithKakao("fictional-id", "fictional-nonce")
            gate.resume.countDown()
            val retry = replay.get(5, TimeUnit.SECONDS)
            if (captureBeforeSwitch) assertEquals("Bearer fictional-access-u1-rotated", retry!!.header("Authorization"))
            else assertNull(retry)
        } finally { gate.resume.countDown(); worker.shutdownNow() }
    }
}
