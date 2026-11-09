package com.nurseflow.auth.api

import com.nurseflow.support.ApiTestBase
import com.nurseflow.support.FakeOidcProvider
import com.nurseflow.support.HttpTestClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.net.URLDecoder
import java.time.Instant
import java.util.UUID

class WebKakaoSessionHttpTest : ApiTestBase() {
    private val webHeaders = mapOf("X-NurseFlow-Client" to "web", "Origin" to FakeOidcProvider.WEB_ORIGIN)

    /** A started login as the browser sees it: Kakao authorize parameters plus the binding cookie. */
    private data class Started(val params: Map<String, String>, val loginCookie: String) {
        val state get() = params.getValue("state")
        val nonce get() = params.getValue("nonce")
    }

    private fun start(redirectUri: String = FakeOidcProvider.REDIRECT_URI, headers: Map<String, String> = webHeaders) =
        http.post("/api/v1/auth/web/kakao/authorization", mapOf("redirectUri" to redirectUri), headers = headers)

    private fun started(): Started {
        val response = start()
        assertEquals(200, response.status, response.body)
        val url = URI(response.json!!.get("authorizationUrl").asString())
        val params = url.rawQuery.split("&").associate {
            val (k, v) = it.split("=", limit = 2)
            k to URLDecoder.decode(v, Charsets.UTF_8)
        }
        return Started(params, response.cookie("nf_kakao_login")!!)
    }

    private fun exchange(code: String, state: String, cookie: String?, headers: Map<String, String> = webHeaders) =
        http.post(
            "/api/v1/auth/web/kakao/code",
            mapOf("code" to code, "state" to state),
            headers = headers + (cookie?.let { mapOf("Cookie" to it) } ?: emptyMap()),
        )

    private fun login(subject: String = "fake-web-${UUID.randomUUID()}"): HttpTestClient.Response {
        val s = started()
        return exchange(FakeOidcProvider.approve(subject, s.nonce), s.state, s.loginCookie)
    }

    /** `name=value` of the Set-Cookie header with that name, for sending back as a Cookie header. */
    private fun HttpTestClient.Response.cookie(name: String): String? = setCookie(name)?.substringBefore(';')

    private fun HttpTestClient.Response.setCookie(name: String): String? =
        headers.entries.filter { it.key.equals("Set-Cookie", true) }.flatMap { it.value }.firstOrNull { it.startsWith("$name=") }

    private fun assertWebCookie(setCookie: String?) {
        requireNotNull(setCookie)
        listOf("HttpOnly", "Secure", "SameSite=Strict", "Path=/api/v1/auth/web").forEach {
            assertTrue(setCookie.contains(it), "$it missing in cookie attributes")
        }
    }

    @Test
    fun `start returns a kakao authorize url with server generated state and nonce and a binding cookie`() {
        val response = start()

        assertEquals(200, response.status, response.body)
        assertEquals("no-store", response.header("Cache-Control"))
        assertWebCookie(response.setCookie("nf_kakao_login"))
        val url = response.json!!.get("authorizationUrl").asString()
        assertTrue(url.startsWith(FakeOidcProvider.AUTHORIZE_URI + "?"), url)
        val s = started()
        assertEquals("code", s.params["response_type"])
        assertEquals(FakeOidcProvider.REST_API_KEY, s.params["client_id"])
        assertEquals(FakeOidcProvider.REDIRECT_URI, s.params["redirect_uri"])
        assertTrue(s.state.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        assertTrue(s.nonce.matches(Regex("^[A-Za-z0-9_-]{43}$")))
        assertFalse(s.loginCookie.contains(s.state), "binding cookie must not be the state itself")
        assertEquals(0, jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth_web_login_attempts WHERE state_hash=?", Int::class.java, s.state))
    }

    @Test
    fun `code login issues a cookie session and keeps the refresh token out of the body`() {
        val response = login()

        assertEquals(200, response.status, response.body)
        assertEquals("no-store", response.header("Cache-Control"))
        val body = response.json!!
        assertFalse(body.has("refreshToken"))
        assertTrue(body.get("user").get("newUser").asBoolean())
        assertWebCookie(response.setCookie("nf_refresh"))
        assertTrue(response.setCookie("nf_kakao_login")!!.contains("Max-Age=0"))
        assertEquals(200, http.get("/api/v1/me", body.get("accessToken").asString()).status)
    }

    @Test
    fun `state must belong to this browser`() {
        val mine = started()
        val other = started()
        val code = FakeOidcProvider.approve("fake-web-csrf", other.nonce)

        assertEquals("INVALID_LOGIN_STATE", exchange(code, other.state, mine.loginCookie).code)
        assertEquals("INVALID_LOGIN_STATE", exchange(code, other.state, cookie = null).code)
        assertEquals("INVALID_LOGIN_STATE", exchange(code, "A".repeat(43), other.loginCookie).code)
    }

    @Test
    fun `state is single use even when the first exchange fails`() {
        val s = started()
        assertEquals("INVALID_AUTHORIZATION_CODE", exchange("code-never-issued", s.state, s.loginCookie).code)

        val code = FakeOidcProvider.approve("fake-web-replay", s.nonce)
        assertEquals("INVALID_LOGIN_STATE", exchange(code, s.state, s.loginCookie).code)
    }

    @Test
    fun `successful state cannot be replayed`() {
        val s = started()
        assertEquals(200, exchange(FakeOidcProvider.approve("fake-web-once", s.nonce), s.state, s.loginCookie).status)

        val before = FakeOidcProvider.tokenRequests.get()
        val replay = exchange(FakeOidcProvider.approve("fake-web-once", s.nonce), s.state, s.loginCookie)
        assertEquals("INVALID_LOGIN_STATE", replay.code)
        assertEquals(before, FakeOidcProvider.tokenRequests.get(), "replayed state must not reach Kakao")
    }

    @Test
    fun `authorization codes are single use`() {
        val first = started()
        val code = FakeOidcProvider.approve("fake-web-code", first.nonce)
        assertEquals(200, exchange(code, first.state, first.loginCookie).status)

        val second = started()
        assertEquals("INVALID_AUTHORIZATION_CODE", exchange(code, second.state, second.loginCookie).code)
    }

    @Test
    fun `expired login attempts are rejected`() {
        val s = started()
        jdbc.update("UPDATE auth_web_login_attempts SET expires_at=?", Instant.now().minusSeconds(1).toString())

        assertEquals("INVALID_LOGIN_STATE",
            exchange(FakeOidcProvider.approve("fake-web-late", s.nonce), s.state, s.loginCookie).code)
    }

    @Test
    fun `id token nonce and audience must match the server's login attempt`() {
        val s = started()
        assertEquals("INVALID_ID_TOKEN",
            exchange(FakeOidcProvider.approve("fake-web-nonce", nonce = "other-nonce"), s.state, s.loginCookie).code)

        val t = started()
        val androidAudience = FakeOidcProvider.approve("fake-web-aud", t.nonce, audience = FakeOidcProvider.ANDROID_APP_KEY)
        assertEquals("INVALID_ID_TOKEN", exchange(androidAudience, t.state, t.loginCookie).code)
    }

    @Test
    fun `redirect uri must be on the allow-list`() {
        assertEquals("INVALID_REDIRECT_URI", start("https://evil.example/auth/kakao/callback").code)
        assertEquals("INVALID_REDIRECT_URI", start(FakeOidcProvider.REDIRECT_URI + "?next=/x").code)
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM auth_web_login_attempts", Int::class.java))
    }

    @Test
    fun `web routes require the client header and an allowed origin`() {
        assertEquals("WEB_CLIENT_REQUIRED", start(headers = mapOf("Origin" to FakeOidcProvider.WEB_ORIGIN)).code)
        assertEquals("WEB_CLIENT_REQUIRED", start(headers = mapOf("X-NurseFlow-Client" to "web", "Origin" to "https://evil.example")).code)
        assertEquals("WEB_CLIENT_REQUIRED", start(headers = webHeaders + ("Sec-Fetch-Site" to "cross-site")).code)
        assertEquals(403, http.post("/api/v1/auth/web/session/refresh", null).status)
        assertEquals(403, http.post("/api/v1/auth/web/logout", null).status)
        // Non-browser clients send no Origin; the custom header is still required.
        assertEquals(200, start(headers = mapOf("X-NurseFlow-Client" to "web")).status)
    }

    @Test
    fun `cookie refresh rotates, reuse revokes the session, logout clears the cookie`() {
        val first = login().cookie("nf_refresh")!!

        val refreshed = http.post("/api/v1/auth/web/session/refresh", null, headers = webHeaders + ("Cookie" to first))
        assertEquals(200, refreshed.status, refreshed.body)
        assertFalse(refreshed.json!!.has("refreshToken"))
        assertWebCookie(refreshed.setCookie("nf_refresh"))
        val second = refreshed.cookie("nf_refresh")!!
        assertNotEquals(first, second)

        // Replaying the rotated cookie is treated as theft and revokes the whole family.
        assertEquals("INVALID_REFRESH_TOKEN",
            http.post("/api/v1/auth/web/session/refresh", null, headers = webHeaders + ("Cookie" to first)).code)
        assertEquals("INVALID_REFRESH_TOKEN",
            http.post("/api/v1/auth/web/session/refresh", null, headers = webHeaders + ("Cookie" to second)).code)

        val third = login().cookie("nf_refresh")!!
        val logout = http.post("/api/v1/auth/web/logout", null, headers = webHeaders + ("Cookie" to third))
        assertEquals(204, logout.status)
        assertTrue(logout.setCookie("nf_refresh")!!.contains("Max-Age=0"))
        assertEquals("INVALID_REFRESH_TOKEN",
            http.post("/api/v1/auth/web/session/refresh", null, headers = webHeaders + ("Cookie" to third)).code)
    }

    @Test
    fun `refresh without a cookie is rejected and logout without one is idempotent`() {
        assertEquals("INVALID_REFRESH_TOKEN", http.post("/api/v1/auth/web/session/refresh", null, headers = webHeaders).code)
        assertEquals("INVALID_REFRESH_TOKEN",
            http.post("/api/v1/auth/web/session/refresh", null, headers = webHeaders + ("Cookie" to "nf_refresh=not-a-token")).code)
        assertEquals(204, http.post("/api/v1/auth/web/logout", null, headers = webHeaders).status)
    }

    @Test
    fun `android body flow is unchanged and does not set cookies`() {
        val session = http.post("/api/v1/auth/kakao/session", mapOf("idToken" to FakeOidcProvider.idToken("fake-android-a2")))

        assertEquals(200, session.status, session.body)
        assertTrue(session.json!!.has("refreshToken"))
        assertNull(session.header("Set-Cookie"))
        // The REST API key is only valid for the server-side web exchange, not for direct ID token login.
        assertEquals("INVALID_ID_TOKEN", http.post("/api/v1/auth/kakao/session",
            mapOf("idToken" to FakeOidcProvider.idToken("fake-android-a2", audience = FakeOidcProvider.REST_API_KEY))).code)
    }

    @Test
    fun `malformed web bodies are INVALID_REQUEST`() {
        val response = http.post("/api/v1/auth/web/kakao/code", mapOf("code" to "", "state" to "short"), headers = webHeaders)

        assertEquals("INVALID_REQUEST", response.code)
        assertEquals(listOf("code", "state"), response.fieldNames)
    }
}
