package com.nurseflow.support

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in device fixture. Never packaged in the application; binds only to loopback. */
@EnabledIfEnvironmentVariable(named = "NURSEFLOW_ANDROID_E2E", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = [
    "server.address=127.0.0.1", "server.port=18081",
    "spring.datasource.url=jdbc:h2:mem:android_session_e2e;DB_CLOSE_DELAY=-1",
    "nurseflow.auth.session.access-token-ttl=2s",
    "nurseflow.auth.kakao.audiences=nurseflow-test-android",
])
class AndroidSessionE2eServerTest {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mapper: JsonMapper

    @Test fun serveFictionalAccountsUntilDeviceCompletes() {
        val nonce = "fictional-device-nonce"
        listOf("admin" to "WARD_ADMIN", "member" to "WARD_MEMBER").forEach { (account, role) ->
            val user = UUID.randomUUID()
            val ward = UUID.randomUUID()
            val now = Instant.now().toString()
            jdbc.update("INSERT INTO users(user_id,provider,provider_subject,display_name,created_at,last_login_at) VALUES(?,'KAKAO',?,?,?,?)",
                user, "device-$account", "가상 $account", now, now)
            jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,'Asia/Seoul')", ward, "가상 $account 병동")
            jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,?,TRUE)", ward, user.toString(), role)
        }
        val complete = CountDownLatch(1)
        val passed = AtomicBoolean(false)
        val controls = HttpServer.create(InetSocketAddress("127.0.0.1", 18082), 0)
        controls.createContext("/identity") { exchange ->
            val account = exchange.requestURI.rawQuery?.removePrefix("account=")
            val allowed = account in setOf("admin", "member")
            val body = if (allowed) mapper.writeValueAsBytes(mapOf(
                "idToken" to FakeOidcProvider.idToken("device-$account", nonce = nonce, nickname = "가상 $account"),
                "nonce" to nonce,
            )) else "{}".toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (allowed) 200 else 400, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        controls.createContext("/stats") { exchange ->
            val body = mapper.writeValueAsBytes(mapOf(
                "rotated" to jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_tokens WHERE replaced_at IS NOT NULL", Int::class.java),
                "revoked" to jdbc.queryForObject("SELECT COUNT(*) FROM auth_refresh_tokens WHERE revoked_at IS NOT NULL", Int::class.java),
            ))
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        controls.createContext("/finish") { exchange ->
            passed.set(exchange.requestURI.rawQuery == "passed=true")
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            complete.countDown()
        }
        controls.start()
        try {
            assertTrue(complete.await(10, TimeUnit.MINUTES), "Device test did not complete within ten minutes")
            assertTrue(passed.get(), "Device reported a failed or incomplete E2E test")
        } finally { controls.stop(0) }
    }

    companion object {
        private val sessionSecret = UUID.randomUUID().toString() + UUID.randomUUID()
        @JvmStatic @DynamicPropertySource
        fun configure(registry: DynamicPropertyRegistry) {
            registry.add("nurseflow.auth.kakao.issuer") { FakeOidcProvider.ISSUER }
            registry.add("nurseflow.auth.kakao.jwk-set-uri") { FakeOidcProvider.jwkSetUri }
            registry.add("nurseflow.auth.session.secret") { sessionSecret }
        }
    }
}
