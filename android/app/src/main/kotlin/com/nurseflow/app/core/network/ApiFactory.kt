package com.nurseflow.app.core.network

import com.nurseflow.app.core.session.SessionManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiJson {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }
}

object ApiFactory {
    private fun baseClient() = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // A rotated refresh token must not be replayed automatically.
        // No body logging: requests carry tokens and personal data.

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(ApiJson.json.asConverterFactory("application/json".toMediaType()))
        .build()

    fun authApi(baseUrl: String): AuthApi = retrofit(baseUrl, baseClient()
        // Authentication is infrequent. Open a fresh connection rather than reusing an idle
        // socket: retrying a refresh after a lost response could replay a consumed token.
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .build()).create(AuthApi::class.java)

    fun nurseFlowApi(baseUrl: String, session: SessionManager): NurseFlowApi {
        val client = baseClient()
            // This interface contains read-only GETs. Recover stale pooled connections;
            // token rotation uses the separate, non-retrying auth client above.
            .retryOnConnectionFailure(true)
            .addInterceptor(BearerInterceptor(session))
            .authenticator(RefreshAuthenticator(session))
            .build()
        return retrofit(baseUrl, client).create(NurseFlowApi::class.java)
    }

    /**
     * Invitation writes (issue, reissue, revoke, redeem) are not idempotent or not safely
     * replayable, so this client never retries on a connection failure and never reuses an idle
     * socket that might already be closed. A lost response surfaces as a NETWORK error and the
     * screen resolves it explicitly. 401 still refreshes once: the server did not process it.
     */
    fun invitationApi(baseUrl: String, session: SessionManager): InvitationApi =
        retrofit(baseUrl, nonRetryingClient(session).build()).create(InvitationApi::class.java)

    /**
     * Hospital search and ward creation. Same non-retrying policy as [invitationApi]; creation
     * is protected by an Idempotency-Key the screen reuses for an explicit retry. The read
     * timeout is longer because the server may fall back to a live lookup.
     */
    fun wardCreationApi(baseUrl: String, session: SessionManager): WardCreationApi =
        retrofit(baseUrl, nonRetryingClient(session).readTimeout(30, TimeUnit.SECONDS).build())
            .create(WardCreationApi::class.java)

    private fun nonRetryingClient(session: SessionManager) = baseClient()
        .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
        .addInterceptor(BearerInterceptor(session))
        .authenticator(RefreshAuthenticator(session))
}

private class BearerInterceptor(private val session: SessionManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.accessToken ?: return chain.proceed(chain.request())
        return chain.proceed(chain.request().newBuilder().header("Authorization", "Bearer $token").build())
    }
}

/**
 * On 401, refreshes the session once (shared by concurrent requests) and retries.
 * A rejected refresh ends the session; transient failures preserve it for a later retry.
 */
private class RefreshAuthenticator(private val session: SessionManager) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.priorResponse != null) return null // already retried once
        val used = response.request.header("Authorization")?.removePrefix("Bearer ")
        // Any failure here must not escape: OkHttp would rethrow it on its own thread.
        val fresh = try {
            runBlocking { session.refreshAfterUnauthorized(used) }
        } catch (_: Exception) {
            null
        } ?: return null
        return response.request.newBuilder().header("Authorization", "Bearer $fresh").build()
    }
}
