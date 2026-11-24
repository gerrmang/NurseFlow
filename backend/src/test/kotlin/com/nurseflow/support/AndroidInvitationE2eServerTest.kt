package com.nurseflow.support

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
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

/**
 * Opt-in, loopback-only device fixture for B3a. Real invitation services and HTTP; fictional OIDC
 * accounts and an in-memory DB. Production code, schema and API are unchanged: the only extra
 * route is this test's own control server, and the link base is a test property.
 */
@EnabledIfEnvironmentVariable(named = "NURSEFLOW_INVITE_E2E", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = [
    "server.address=127.0.0.1", "server.port=18085",
    "spring.datasource.url=jdbc:h2:mem:android_invite_e2e;DB_CLOSE_DELAY=-1",
    "nurseflow.auth.kakao.audiences=nurseflow-test-android",
    "nurseflow.invitation.link-base-url=nurseflow://invite",
])
class AndroidInvitationE2eServerTest {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mapper: JsonMapper

    @Test fun serveInvitationScenarioUntilDeviceCompletes() {
        val ward = UUID.randomUUID()
        val admin = UUID.randomUUID()
        val now = Instant.now().toString()
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone,hospital_name) VALUES(?,?,'Asia/Seoul',?)", ward, "가상 초대 병동", "가상 시범 병원")
        jdbc.update("INSERT INTO users(user_id,provider,provider_subject,display_name,created_at,last_login_at) VALUES(?,'KAKAO',?,?,?,?)",
            admin, "invite-admin", "가상 admin", now, now)
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,'WARD_ADMIN',TRUE)",
            ward, admin.toString())
        // A fictional nurse record the admin links to a member invitation; no account yet.
        jdbc.update("INSERT INTO nurses(nurse_id,ward_id,subject,display_name,qualifications,active) VALUES(?,?,NULL,?,'[]',TRUE)",
            UUID.randomUUID(), ward, "가상 미가입 간호사")
        // admin: ward admin. nurse/admin2/other: Kakao-only accounts created on first login.
        val accounts = setOf("admin", "nurse", "admin2", "other")

        val complete = CountDownLatch(1)
        val passed = AtomicBoolean(false)
        val controls = HttpServer.create(InetSocketAddress("127.0.0.1", 18086), 0)
        controls.createContext("/identity") { exchange ->
            val name = exchange.requestURI.rawQuery?.removePrefix("account=")
            val allowed = name in accounts
            val payload = if (allowed) mapOf(
                "idToken" to FakeOidcProvider.idToken("invite-$name", nonce = "fictional-invite-nonce", nickname = "가상 $name"),
                "nonce" to "fictional-invite-nonce",
            ) else emptyMap()
            val body = mapper.writeValueAsBytes(payload)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (allowed) 200 else 400, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        controls.createContext("/expire") { exchange ->
            // Moves one invitation of this ward past its fixed 24h expiry, instead of waiting a day.
            val id = exchange.requestURI.rawQuery?.removePrefix("invitationId=")
            val updated = if (exchange.requestMethod == "POST" && id != null) runCatching {
                jdbc.update("UPDATE ward_invitations SET expires_at=? WHERE invitation_id=? AND ward_id=?",
                    Instant.now().minusSeconds(60).toString(), UUID.fromString(id), ward)
            }.getOrDefault(0) else 0
            exchange.sendResponseHeaders(if (updated == 1) 200 else 400, -1)
            exchange.close()
        }
        controls.createContext("/finish") { exchange ->
            passed.set(exchange.requestURI.rawQuery == "passed=true")
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
            complete.countDown()
        }
        controls.start()
        try {
            assertTrue(complete.await(10, TimeUnit.MINUTES), "Invitation device test timed out")
            assertTrue(passed.get(), "Invitation device test failed or did not complete")
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
