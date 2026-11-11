package com.nurseflow.auth.application

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.nurseflow.auth.application.service.KakaoTokenClient
import com.nurseflow.auth.config.AuthProperties
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.support.FakeOidcProvider
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.slf4j.LoggerFactory
import tools.jackson.databind.json.JsonMapper

/** Diagnostics of rejected token exchanges: only Kakao's safe error code reaches the log. */
class KakaoTokenClientTest {
    private val appender = ListAppender<ILoggingEvent>()
    private val logger = LoggerFactory.getLogger(KakaoTokenClient::class.java) as Logger

    @BeforeEach
    fun attach() {
        appender.start()
        logger.addAppender(appender)
    }

    @AfterEach
    fun detach() {
        logger.detachAppender(appender)
    }

    private fun client(secret: String) = KakaoTokenClient(
        AuthProperties(
            kakao = AuthProperties.Kakao(
                tokenUri = FakeOidcProvider.tokenUri,
                restApiKey = FakeOidcProvider.REST_API_KEY,
                clientSecret = secret,
                redirectUris = listOf(FakeOidcProvider.REDIRECT_URI),
            ),
        ),
        JsonMapper(),
    )

    @Test
    fun `a missing client secret is logged as KOE010 without secrets or codes`() {
        val code = FakeOidcProvider.approve("fake-diag", "nonce-diag")

        val ex = assertThrows<BusinessException> { client(secret = "").exchangeForIdToken(code, FakeOidcProvider.REDIRECT_URI) }

        assertEquals(ErrorCode.INVALID_AUTHORIZATION_CODE, ex.errorCode)
        val logged = appender.list.joinToString("\n") { it.formattedMessage }
        assertEquals("Kakao token exchange rejected: status=401 kakaoErrorCode=KOE010", logged)
        listOf(code, FakeOidcProvider.REST_API_KEY, FakeOidcProvider.CLIENT_SECRET, "Bad client credentials").forEach {
            assertFalse(logged.contains(it), "log must not contain $it")
        }
    }

    @Test
    fun `an invalid code is logged with its Kakao error code`() {
        val ex = assertThrows<BusinessException> {
            client(secret = FakeOidcProvider.CLIENT_SECRET).exchangeForIdToken("code-never-issued", FakeOidcProvider.REDIRECT_URI)
        }

        assertEquals(ErrorCode.INVALID_AUTHORIZATION_CODE, ex.errorCode)
        assertEquals("Kakao token exchange rejected: status=400 kakaoErrorCode=KOE320", appender.list.single().formattedMessage)
    }
}
