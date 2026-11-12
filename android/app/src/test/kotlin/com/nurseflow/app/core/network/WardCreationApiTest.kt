package com.nurseflow.app.core.network

import com.nurseflow.app.core.session.MemoryTokenStore
import com.nurseflow.app.core.session.SessionManager
import com.nurseflow.app.feature.ward.create.CreateStep
import com.nurseflow.app.feature.ward.create.SearchState
import com.nurseflow.app.feature.ward.create.WardCreateFailure
import com.nurseflow.app.feature.ward.create.WardCreateState
import com.nurseflow.app.feature.ward.create.WardCreateViewModel
import com.nurseflow.app.feature.ward.create.toWardCreateFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Contract tests for hospital search and ward creation against a MockWebServer. These prove how
 * the client talks to a server that behaves as documented. They do NOT prove the real server's
 * behaviour: that needs the real-integration procedure in android/B4a-verification.md.
 */
class WardCreationApiTest {
    private val sessionJson = """{"accessToken":"fake-access","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fake-refresh","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"demo-user","displayName":"가상 사용자"}}"""
    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    private fun problem(status: Int, code: String) =
        MockResponse().setResponseCode(status).setHeader("Content-Type", "application/problem+json").setBody("""{"status":$status,"code":"$code","detail":"untrusted"}""")

    private fun signedIn(server: MockWebServer): WardCreationApi {
        // Keep retries on the same endpoint. With dual-stack localhost, OkHttp can postpone
        // the disconnected IPv4 route and try IPv6, where this server is not listening.
        // Production writes must remain non-retrying; make the fault fixture deterministic.
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        val base = "http://127.0.0.1:${server.port}/"
        val manager = SessionManager(ApiFactory.authApi(base), MemoryTokenStore())
        runBlocking { manager.loginWithKakao("fake-id", "fake-nonce") }
        return ApiFactory.wardCreationApi(base, manager)
    }

    /**
     * A tiny stateful stand-in for POST /api/v1/wards: records Idempotency-Keys, keeps replay rows
     * (key -> body + result) only for successes, refuses the same key with another body, and refuses
     * the same hospital and name from the same account with a different key (WARD_ALREADY_CREATED).
     */
    private class FakeWardServer : Dispatcher() {
        val sessionJson = """{"accessToken":"fake-access","accessTokenExpiresAt":"2099-01-01T00:00:00Z","refreshToken":"fake-refresh","refreshTokenExpiresAt":"2099-02-01T00:00:00Z","user":{"userId":"demo-user","displayName":"가상 사용자"}}"""
        val keys = mutableListOf<String?>()
        val authorizations = mutableListOf<String?>()
        val wards = mutableListOf<Pair<String, String>>() // (externalId, wardName)
        private val replays = mutableMapOf<String, Pair<String, String>>() // key -> (body, response)
        /** Policies for POST /wards, consumed in order; the default is to answer normally. */
        val policies = ArrayDeque<SocketPolicy>()
        var postCount = 0

        @Synchronized override fun dispatch(request: RecordedRequest): MockResponse {
            if (request.path == "/api/v1/auth/kakao/session") return json(sessionJson)
            if (request.method != "POST" || request.path != "/api/v1/wards") return MockResponse().setResponseCode(404)
            postCount++
            val policy = policies.removeFirstOrNull() ?: SocketPolicy.KEEP_OPEN
            // The connection dies before the server processes anything: nothing is stored. (A dispatcher cannot
            // make MockWebServer disconnect at accept time, so the request is read but ignored.)
            if (policy == SocketPolicy.DISCONNECT_AT_START) return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST)
            val key = request.getHeader("Idempotency-Key")
            keys += key
            authorizations += request.getHeader("Authorization")
            val bodyText = request.body.clone().readUtf8() // keep the recorded body readable for the test
            val body = Json.parseToJsonElement(bodyText).jsonObject
            val externalId = body["hospital"]!!.jsonObject["externalId"]!!.jsonPrimitive.content
            val wardName = body["wardName"]!!.jsonPrimitive.content.trim()
            val replay = replays[key]
            val response = when {
                replay != null && replay.first == bodyText -> json(replay.second, 201)
                replay != null -> problem(409, "IDEMPOTENCY_KEY_REUSED")
                wards.any { it == externalId to wardName } -> problem(409, "WARD_ALREADY_CREATED")
                else -> {
                    wards += externalId to wardName
                    val result = """{"wardId":"ward-${wards.size}","wardName":"$wardName","roles":["WARD_ADMIN"],"canViewWardCalendar":true,"hospitalName":"가상대학교병원"}"""
                    replays[key!!] = bodyText to result
                    json(result, 201)
                }
            }
            // The ward is already created and stored above: only the response is lost.
            return if (policy == SocketPolicy.DISCONNECT_AFTER_REQUEST) MockResponse().setSocketPolicy(policy) else response
        }

        private fun json(body: String, code: Int = 200) =
            MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
        private fun problem(status: Int, code: String) =
            MockResponse().setResponseCode(status).setHeader("Content-Type", "application/problem+json").setBody("""{"status":$status,"code":"$code"}""")
    }

    private fun candidate(id: String) = HospitalCandidateDto("TEST_SOURCE", id, "가상대학교병원", "가상시 가상구 1", "종합병원")

    // ---- request shape ----

    @Test fun searchPathEncodesKoreanQueryAndSendsPageAndSize() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) =
                    if (request.path!!.startsWith("/api/v1/auth/")) json(sessionJson)
                    else json("""{"items":[{"source":"TEST_SOURCE","externalId":"h1","name":"가상대학교병원","address":"가상시 가상구 1","category":"종합병원"},{"source":"TEST_SOURCE","externalId":"h2","name":"가상대학교병원","address":"가상시 가상동 2"}],"page":1,"size":20,"totalCount":2,"attribution":"가상 출처"}""")
            }
            val api = signedIn(server)
            val page = api.searchHospitals("가상 병원")
            assertEquals(2, page.items.size)
            assertEquals("종합병원", page.items[0].category)
            assertNull(page.items[1].category)
            assertEquals("가상 출처", page.attribution)
            server.takeRequest(1, TimeUnit.SECONDS) // login
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("GET", request.method)
            assertEquals("/api/v1/hospitals/search", request.requestUrl!!.encodedPath)
            assertEquals("가상 병원", request.requestUrl!!.queryParameter("query"))
            assertEquals("1", request.requestUrl!!.queryParameter("page"))
            assertEquals("20", request.requestUrl!!.queryParameter("size"))
            assertEquals("Bearer fake-access", request.getHeader("Authorization"))
        }
    }

    @Test fun emptySearchResultIsA200WithNoItems() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) =
                    if (request.path!!.startsWith("/api/v1/auth/")) json(sessionJson)
                    else json("""{"items":[],"page":1,"size":20,"totalCount":0,"attribution":"가상 출처"}""")
            }
            val page = signedIn(server).searchHospitals("가상 병원")
            assertTrue(page.items.isEmpty())
            assertEquals("가상 출처", page.attribution)
        }
    }

    @Test fun createSendsIdempotencyKeyBearerAndExactlyTheContractBody() = runBlocking {
        MockWebServer().use { server ->
            val fake = FakeWardServer()
            server.dispatcher = fake
            val api = signedIn(server)
            val created = api.createWard("0b5f8e0e-1111-4222-8333-444455556666", CreateWardBody(HospitalRefBody("TEST_SOURCE", "h1"), "가상 내과 병동"))
            assertEquals("가상 내과 병동", created.wardName)
            assertTrue(created.isAdmin)
            assertEquals("가상대학교병원", created.hospitalName)
            server.takeRequest(1, TimeUnit.SECONDS) // login
            val request = server.takeRequest(1, TimeUnit.SECONDS)!!
            assertEquals("POST /api/v1/wards", "${request.method} ${request.path}")
            assertEquals("0b5f8e0e-1111-4222-8333-444455556666", request.getHeader("Idempotency-Key"))
            assertEquals("Bearer fake-access", request.getHeader("Authorization"))
            val body: JsonObject = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
            assertEquals(setOf("hospital", "wardName"), body.keys) // no timeZone, name or address at the top
            assertEquals(setOf("source", "externalId"), body["hospital"]!!.jsonObject.keys) // no name or address
        }
    }

    @Test fun problemCodesFromTheServerAreParsedAndClassified() = runBlocking {
        val cases = listOf(
            Triple(503, "HOSPITAL_SEARCH_NOT_CONFIGURED", WardCreateFailure.NOT_CONFIGURED),
            Triple(503, "HOSPITAL_SEARCH_UNAVAILABLE", WardCreateFailure.UNAVAILABLE),
            Triple(409, "WARD_ALREADY_CREATED", WardCreateFailure.ALREADY_CREATED),
            Triple(409, "WARD_CREATION_LIMIT_REACHED", WardCreateFailure.LIMIT_REACHED),
            Triple(409, "IDEMPOTENCY_KEY_REUSED", WardCreateFailure.KEY_REUSED),
            Triple(422, "HOSPITAL_NOT_FOUND", WardCreateFailure.HOSPITAL_NOT_FOUND),
            Triple(400, "INVALID_REQUEST", WardCreateFailure.INVALID_REQUEST),
        )
        for ((status, code, expected) in cases) {
            MockWebServer().use { server ->
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest) =
                        if (request.path!!.startsWith("/api/v1/auth/")) json(sessionJson) else problem(status, code)
                }
                val api = signedIn(server)
                val create = try { apiCall { api.createWard("k", CreateWardBody(HospitalRefBody("S", "h"), "가상")) }; null } catch (e: ApiException) { e }
                assertEquals(status, create!!.status)
                assertEquals(code, create.code)
                assertEquals(expected, create.toWardCreateFailure())
                val search = try { apiCall { api.searchHospitals("가상 병원") }; null } catch (e: ApiException) { e }
                assertEquals(expected, search!!.toWardCreateFailure())
            }
        }
    }

    @Test fun lostCreateResponseIsNotRetriedAutomatically() = runBlocking {
        MockWebServer().use { server ->
            val fake = FakeWardServer()
            fake.policies += SocketPolicy.DISCONNECT_AFTER_REQUEST
            server.dispatcher = fake
            val api = signedIn(server)
            val failure = try { apiCall { api.createWard("k", CreateWardBody(HospitalRefBody("S", "h"), "가상")) }; null } catch (e: ApiException) { e.toWardCreateFailure() }
            assertEquals(WardCreateFailure.NETWORK, failure)
            assertEquals("The create request must be sent exactly once", 1, fake.postCount)
        }
    }

    // ---- lost response, (a) before processing vs (b) after the ward was created ----

    private fun <T> withVm(block: (WardCreateViewModel, FakeWardServer) -> T): T {
        val server = MockWebServer()
        val fake = FakeWardServer()
        server.dispatcher = fake
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val api = signedIn(server)
            return block(WardCreateViewModel(api, scope), fake)
        } finally {
            scope.cancel()
            server.shutdown()
        }
    }

    private fun WardCreateViewModel.waitFor(condition: (WardCreateState) -> Boolean): WardCreateState =
        runBlocking { withTimeout(10_000) { state.first(condition) } }

    private fun WardCreateViewModel.submit(hospital: String, name: String): WardCreateState {
        select(candidate(hospital))
        setWardName(name)
        create()
        return waitFor { !it.creating }
    }

    @Test fun caseA_requestNeverReachedTheServer_nothingWasCreatedAndTheSameKeyCreatesOnce() = withVm { vm, server ->
        server.policies += SocketPolicy.DISCONNECT_AT_START
        val lost = vm.submit("h1", "가상 내과 병동")
        assertTrue(lost.lostResponse) // the client cannot tell (a) from (b) ...
        assertEquals(WardCreateFailure.NETWORK, lost.createError)
        assertEquals(0, server.wards.size) // ... but here the server never processed anything
        assertTrue(server.keys.isEmpty())

        vm.create()
        val done = vm.waitFor { it.created != null }
        assertEquals(CreateStep.DONE, done.step)
        assertEquals(1, server.wards.size)
    }

    @Test fun caseB_wardCreatedButResponseLost_sameKeyRetryReplaysAndExactlyOneWardExists() = withVm { vm, server ->
        server.policies += SocketPolicy.DISCONNECT_AFTER_REQUEST
        val lost = vm.submit("h1", "가상 내과 병동")
        assertEquals(WardCreateFailure.NETWORK, lost.createError)
        assertTrue(lost.lostResponse)
        assertNull(lost.created)
        assertEquals("The server created the ward before the response was lost", 1, server.wards.size)
        assertFalse(lost.inputChangedSinceLoss)

        vm.create() // unchanged retry from the ViewModel
        val done = vm.waitFor { it.created != null }
        assertEquals(CreateStep.DONE, done.step) // UI state lands on created
        assertEquals("ward-1", done.created!!.wardId) // the first ward is replayed
        assertEquals(2, server.postCount)
        assertEquals("The retry must send the very same Idempotency-Key", server.keys[0], server.keys[1])
        assertNotNull(server.keys[0])
        assertEquals("Exactly one ward exists", 1, server.wards.size)
        assertTrue(server.authorizations.all { it == "Bearer fake-access" })
    }

    @Test fun caseB_changedInputAfterLossUsesANewKeyAndIsWarned() = withVm { vm, server ->
        server.policies += SocketPolicy.DISCONNECT_AFTER_REQUEST
        vm.submit("h1", "가상 내과 병동")
        vm.setWardName("가상 외과 병동")
        assertTrue(vm.state.value.inputChangedSinceLoss)
        vm.create()
        vm.waitFor { it.created != null }
        assertNotEquals(server.keys[0], server.keys[1])
        // Different content is a different request: the first (lost) ward and this one both exist.
        assertEquals(2, server.wards.size)
    }

    @Test fun caseB_duplicateGuardAnswersAlreadyCreatedWhenTheOriginalContentIsSentWithAFreshKey() = withVm { vm, server ->
        // 1) Ward A is created but its response is lost (key 1).
        server.policies += SocketPolicy.DISCONNECT_AFTER_REQUEST
        vm.submit("h1", "가상 내과 병동")
        // 2) The user edits to ward B, whose response is lost as well (key 2 replaces key 1).
        server.policies += SocketPolicy.DISCONNECT_AFTER_REQUEST
        vm.setWardName("가상 외과 병동")
        vm.create()
        vm.waitFor { !it.creating }
        assertEquals(2, server.wards.size)
        // 3) The user goes back to ward A's content: only key 2 is remembered, so this is a new key
        //    and the server's duplicate guard (same account, hospital and name) answers 409.
        vm.setWardName("가상 내과 병동")
        vm.create()
        val state = vm.waitFor { !it.creating && it.createError == WardCreateFailure.ALREADY_CREATED }
        assertNull(state.created)
        assertEquals(WardCreateFailure.ALREADY_CREATED, state.createError)
        assertEquals(3, server.keys.distinct().size)
        assertEquals(2, server.wards.size) // no third ward
    }

    @Test fun reenteringWithTheSameContentAfterALostResponseIsStoppedByTheDuplicateGuard() = withVm { vm, server ->
        server.policies += SocketPolicy.DISCONNECT_AFTER_REQUEST
        vm.submit("h1", "가상 내과 병동")
        vm.reset() // process death or re-entry: the key is gone
        val state = vm.submit("h1", "가상 내과 병동")
        assertEquals(WardCreateFailure.ALREADY_CREATED, state.createError)
        assertEquals(1, server.wards.size)
        assertNotEquals(server.keys[0], server.keys[1])
    }

    @Test fun searchThroughTheViewModelShowsResultsFromTheServer() = runBlocking {
        MockWebServer().use { server ->
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) =
                    if (request.path!!.startsWith("/api/v1/auth/")) json(sessionJson)
                    else json("""{"items":[{"source":"TEST_SOURCE","externalId":"h1","name":"가상대학교병원","address":"가상시 가상구 1"}],"page":1,"size":20,"totalCount":1,"attribution":"가상 출처"}""")
            }
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            try {
                val vm = WardCreateViewModel(signedIn(server), scope)
                vm.setQuery("가상 병원"); vm.search()
                val state = withTimeout(10_000) { vm.state.first { it.search is SearchState.Results } }
                assertEquals("h1", (state.search as SearchState.Results).items.single().externalId)
            } finally { scope.cancel() }
        }
    }
}
