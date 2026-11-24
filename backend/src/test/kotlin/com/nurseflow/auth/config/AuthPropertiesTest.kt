package com.nurseflow.auth.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuthPropertiesTest {
    private fun fromRedirects(vararg uris: String) =
        AuthProperties(kakao = AuthProperties.Kakao(redirectUris = uris.toList())).webAllowedOrigins

    @Test
    fun `redirect uri origins drop the default port like browsers do`() {
        assertEquals(setOf("https://app.example"), fromRedirects("https://app.example:443/auth/kakao/callback"))
        assertEquals(setOf("http://app.example"), fromRedirects("http://app.example:80/auth/kakao/callback"))
    }

    @Test
    fun `non default ports and hosts are kept`() {
        assertEquals(setOf("http://localhost:5173"), fromRedirects("http://localhost:5173/auth/kakao/callback"))
        assertEquals(setOf("https://app.example:8443"), fromRedirects("https://App.Example:8443/callback"))
        assertEquals(setOf("http://app.example:443"), fromRedirects("http://app.example:443/callback"))
    }

    @Test
    fun `explicit allowed origins are normalized the same way`() {
        val properties = AuthProperties(
            kakao = AuthProperties.Kakao(redirectUris = listOf("https://ignored.example/callback")),
            web = AuthProperties.Web(allowedOrigins = listOf("https://App.Example:443/", " http://localhost:5173 ")),
        )

        assertEquals(setOf("https://app.example", "http://localhost:5173"), properties.webAllowedOrigins)
    }
}
