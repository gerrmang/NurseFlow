package com.nurseflow.auth.api

import com.nurseflow.support.ApiTestBase
import com.nurseflow.support.FakeOidcProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class KakaoAuthHttpTest : ApiTestBase() {
    private fun login(idToken: String, nonce: String? = null) =
        http.post("/api/v1/auth/kakao/session", buildMap {
            put("idToken", idToken)
            nonce?.let { put("nonce", it) }
        })

    private fun kakaoSubject() = "fake-kakao-${UUID.randomUUID()}"

    @Test
    fun `kakao login creates a session but no ward access`() {
        val response = login(FakeOidcProvider.idToken(kakaoSubject(), nickname = "가상 간호사 A"))

        assertEquals(200, response.status, response.body)
        assertEquals("no-store", response.header("Cache-Control"))
        val body = response.json!!
        assertEquals("Bearer", body.get("tokenType").asString())
        assertTrue(body.get("user").get("newUser").asBoolean())
        val access = body.get("accessToken").asString()

        val me = http.get("/api/v1/me", access)
        assertEquals(200, me.status, me.body)
        assertEquals("가상 간호사 A", me.json!!.get("displayName").asString())
        assertEquals(0, me.json!!.get("memberships").size())
        assertEquals(0, http.get("/api/v1/wards", access).json!!.get("items").size())
    }

    @Test
    fun `same kakao account maps to the same user and web app key is accepted`() {
        val subject = kakaoSubject()
        val first = login(FakeOidcProvider.idToken(subject)).json!!.get("user")
        val second = login(FakeOidcProvider.idToken(subject, audience = FakeOidcProvider.WEB_APP_KEY)).json!!.get("user")

        assertEquals(first.get("userId").asString(), second.get("userId").asString())
        assertFalse(second.get("newUser").asBoolean())
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM users", Int::class.java))
    }

    @Test
    fun `missing nickname falls back to a neutral display name`() {
        val response = login(FakeOidcProvider.idToken(kakaoSubject(), nickname = null))

        assertEquals("카카오 사용자", response.json!!.get("user").get("displayName").asString())
    }

    @Test
    fun `forged or mismatched id tokens are rejected`() {
        val subject = kakaoSubject()
        val cases = mapOf(
            "other signing key" to FakeOidcProvider.idToken(subject, signedByStranger = true),
            "unknown app key" to FakeOidcProvider.idToken(subject, audience = "someone-elses-app"),
            "other issuer" to FakeOidcProvider.idToken(subject, issuer = "https://evil.example"),
            "expired" to FakeOidcProvider.idToken(subject,
                issuedAt = Instant.now().minusSeconds(7200), expiresAt = Instant.now().minusSeconds(3600)),
            "too old" to FakeOidcProvider.idToken(subject,
                issuedAt = Instant.now().minusSeconds(3600), expiresAt = Instant.now().plusSeconds(3600)),
            "not a jwt" to "not-a-jwt",
        )
        cases.forEach { (case, token) ->
            val response = login(token)
            assertEquals(401, response.status, case)
            assertEquals("INVALID_ID_TOKEN", response.code, case)
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM users", Int::class.java))
    }

    @Test
    fun `nonce must round-trip when either side uses it`() {
        val subject = kakaoSubject()

        assertEquals(200, login(FakeOidcProvider.idToken(subject, nonce = "n-123"), nonce = "n-123").status)
        assertEquals("INVALID_ID_TOKEN", login(FakeOidcProvider.idToken(subject, nonce = "n-123"), nonce = "n-999").code)
        assertEquals("INVALID_ID_TOKEN", login(FakeOidcProvider.idToken(subject, nonce = "n-123")).code)
        assertEquals("INVALID_ID_TOKEN", login(FakeOidcProvider.idToken(subject), nonce = "n-123").code)
    }

    @Test
    fun `api rejects missing tokens and raw kakao id tokens`() {
        val noToken = http.get("/api/v1/me")
        val kakaoToken = http.get("/api/v1/me", FakeOidcProvider.idToken(kakaoSubject()))

        assertEquals(401, noToken.status)
        assertEquals("UNAUTHORIZED", noToken.code)
        assertEquals(401, kakaoToken.status)
    }

    @Test
    fun `refresh rotates tokens and keeps the user signed in`() {
        val session = login(FakeOidcProvider.idToken(kakaoSubject())).json!!
        val firstRefresh = session.get("refreshToken").asString()

        val rotated = http.post("/api/v1/auth/session/refresh", mapOf("refreshToken" to firstRefresh))

        assertEquals(200, rotated.status, rotated.body)
        val next = rotated.json!!
        assertNotEquals(firstRefresh, next.get("refreshToken").asString())
        assertEquals(session.get("user").get("userId").asString(), next.get("user").get("userId").asString())
        assertEquals(200, http.get("/api/v1/me", next.get("accessToken").asString()).status)
        assertEquals(200, http.post("/api/v1/auth/session/refresh",
            mapOf("refreshToken" to next.get("refreshToken").asString())).status)
    }

    @Test
    fun `reusing a rotated refresh token revokes the whole session`() {
        val first = login(FakeOidcProvider.idToken(kakaoSubject())).json!!.get("refreshToken").asString()
        val second = http.post("/api/v1/auth/session/refresh", mapOf("refreshToken" to first))
            .json!!.get("refreshToken").asString()

        val replay = http.post("/api/v1/auth/session/refresh", mapOf("refreshToken" to first))
        val legitimate = http.post("/api/v1/auth/session/refresh", mapOf("refreshToken" to second))

        assertEquals(401, replay.status)
        assertEquals("INVALID_REFRESH_TOKEN", replay.code)
        assertEquals(401, legitimate.status, "family must be revoked after reuse")
    }

    @Test
    fun `logout revokes the refresh token and is idempotent`() {
        val refresh = login(FakeOidcProvider.idToken(kakaoSubject())).json!!.get("refreshToken").asString()

        assertEquals(204, http.post("/api/v1/auth/logout", mapOf("refreshToken" to refresh)).status)
        assertEquals(204, http.post("/api/v1/auth/logout", mapOf("refreshToken" to refresh)).status)
        assertEquals("INVALID_REFRESH_TOKEN",
            http.post("/api/v1/auth/session/refresh", mapOf("refreshToken" to refresh)).code)
    }

    @Test
    fun `expired refresh token requires a new login`() {
        val refresh = login(FakeOidcProvider.idToken(kakaoSubject())).json!!.get("refreshToken").asString()
        jdbc.update("UPDATE auth_refresh_tokens SET expires_at=?", Instant.now().minusSeconds(1).toString())

        assertEquals("INVALID_REFRESH_TOKEN",
            http.post("/api/v1/auth/session/refresh", mapOf("refreshToken" to refresh)).code)
    }

    @Test
    fun `refresh tokens are stored only as hashes`() {
        val refresh = login(FakeOidcProvider.idToken(kakaoSubject())).json!!.get("refreshToken").asString()

        val stored = jdbc.queryForList("SELECT token_hash FROM auth_refresh_tokens", String::class.java)
        assertEquals(1, stored.size)
        assertFalse(stored.single()!!.contains(refresh))
        assertEquals(64, stored.single()!!.length)
    }

    @Test
    fun `malformed auth bodies are INVALID_REQUEST with field names`() {
        val blank = http.post("/api/v1/auth/kakao/session", mapOf("idToken" to ""))
        val badRefresh = http.post("/api/v1/auth/session/refresh", mapOf("refreshToken" to "short"))

        assertEquals("INVALID_REQUEST", blank.code)
        assertEquals(listOf("idToken"), blank.fieldNames)
        assertEquals("INVALID_REQUEST", badRefresh.code)
        assertEquals(listOf("refreshToken"), badRefresh.fieldNames)
    }

    @Test
    fun `signed-in user without invitation cannot reach ward data`() {
        val wardId = insertWard()
        val access = login(FakeOidcProvider.idToken(kakaoSubject())).json!!.get("accessToken").asString()

        val rosters = http.get("/api/v1/wards/$wardId/rosters?month=2026-12", access)
        val published = http.get("/api/v1/wards/$wardId/published-rosters?month=2026-12", access)
        val mine = http.get("/api/v1/wards/$wardId/published-rosters/mine?month=2026-12", access)

        listOf(rosters, published, mine).forEach {
            assertEquals(404, it.status, it.body)
            assertEquals("WARD_NOT_FOUND", it.code)
        }
    }
}
