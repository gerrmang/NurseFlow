package com.nurseflow.auth.application.service

import com.nurseflow.auth.config.AuthProperties
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.json.JsonMapper
import java.net.http.HttpClient

/**
 * Exchanges a Kakao authorization code (web login) for tokens on the server, so the client
 * secret never reaches the browser. Only the OIDC ID token is used: Kakao access and refresh
 * tokens are discarded, and neither the code nor any token is stored or logged.
 */
@Component
class KakaoTokenClient(private val properties: AuthProperties, private val mapper: JsonMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * java.net.http rather than HttpURLConnection: the latter drops the body of a 401, so Kakao's
     * `invalid_client` / KOE010 (missing or wrong client secret) was logged as kakaoErrorCode=null.
     */
    private val client: RestClient by lazy {
        val timeout = properties.kakao.tokenExchangeTimeout
        RestClient.builder()
            .requestFactory(JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build()).apply {
                setReadTimeout(timeout)
            })
            .build()
    }

    fun exchangeForIdToken(code: String, redirectUri: String): String {
        val kakao = properties.kakao
        val form = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", kakao.restApiKey.trim())
            add("redirect_uri", redirectUri)
            add("code", code)
            kakao.clientSecret.trim().takeIf { it.isNotEmpty() }?.let { add("client_secret", it) }
        }
        val body = try {
            client.post().uri(kakao.tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(String::class.java)
        } catch (ex: HttpClientErrorException) {
            // Expired, reused or forged code; or a client_id / redirect_uri / secret Kakao rejects.
            log.warn("Kakao token exchange rejected: status={} kakaoErrorCode={}", ex.statusCode.value(), kakaoErrorCode(ex))
            throw BusinessException(ErrorCode.INVALID_AUTHORIZATION_CODE)
        } catch (ex: RestClientException) {
            log.warn("Kakao token endpoint unavailable: {}", ex.javaClass.simpleName)
            throw BusinessException(ErrorCode.IDENTITY_PROVIDER_UNAVAILABLE)
        }
        val idToken = body?.let { runCatching { mapper.readTree(it) }.getOrNull() }
            ?.get("id_token")?.asString()?.takeIf { it.isNotBlank() }
        // No id_token: OpenID Connect is off for the app in Kakao Developers, or scope lacks openid.
        return idToken ?: throw BusinessException(ErrorCode.IDENTITY_PROVIDER_NOT_CONFIGURED)
    }

    /**
     * Kakao's `error_code` (e.g. KOE010, KOE320) helps diagnose setup and contains no user data. Only a short
     * code matching Kakao's format is logged; the response body itself never is.
     */
    private fun kakaoErrorCode(ex: HttpClientErrorException): String? = runCatching {
        mapper.readTree(ex.responseBodyAsString).get("error_code")?.asString()
    }.getOrNull()?.takeIf { SAFE_ERROR_CODE.matches(it) }

    private companion object {
        val SAFE_ERROR_CODE = Regex("^[A-Z]{2,5}[0-9]{1,5}$")
    }
}
