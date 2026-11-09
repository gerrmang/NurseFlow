package com.nurseflow.support

import com.nurseflow.roster.application.model.*
import com.nurseflow.roster.application.service.RosterService
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
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Opt-in, loopback-only device fixture. Real roster service and HTTP; fictional OIDC and in-memory DB. */
@EnabledIfEnvironmentVariable(named = "NURSEFLOW_ROSTER_E2E", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = [
    "server.address=127.0.0.1", "server.port=18083",
    "spring.datasource.url=jdbc:h2:mem:android_roster_e2e;DB_CLOSE_DELAY=-1",
    "nurseflow.auth.kakao.audiences=nurseflow-test-android",
])
class AndroidRosterE2eServerTest {
    @Autowired lateinit var jdbc: JdbcTemplate
    @Autowired lateinit var mapper: JsonMapper
    @Autowired lateinit var roster: RosterService

    @Test fun servePublishedRosterScenarioUntilDeviceCompletes() {
        val ward = UUID.randomUUID()
        val shift = UUID.randomUUID()
        val month = YearMonth.now(ZoneId.of("Asia/Seoul"))
        val users = listOf("admin", "member", "limited").associateWith { UUID.randomUUID() }
        val nurses = listOf("member", "limited").associateWith { UUID.randomUUID() }
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,'Asia/Seoul')", ward, "가상 공개 병동")
        users.forEach { (name, user) ->
            val now = Instant.now().toString()
            jdbc.update("INSERT INTO users(user_id,provider,provider_subject,display_name,created_at,last_login_at) VALUES(?,'KAKAO',?,?,?,?)",
                user, "roster-$name", "가상 $name", now, now)
            jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,?,?)",
                ward, user.toString(), if (name == "admin") "WARD_ADMIN" else "WARD_MEMBER", name != "limited")
        }
        nurses.forEach { (name, nurse) ->
            jdbc.update("INSERT INTO nurses(nurse_id,ward_id,subject,display_name,qualifications,active) VALUES(?,?,?,?,?,TRUE)",
                nurse, ward, users.getValue(name).toString(), "가상 근무자 $name", "[]")
        }
        jdbc.update("""INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight)
            VALUES(?,?,'D','데이','DAY','07:00:00','15:00:00',FALSE)""", shift, ward)

        val complete = CountDownLatch(1)
        val passed = AtomicBoolean(false)
        val controls = HttpServer.create(InetSocketAddress("127.0.0.1", 18084), 0)
        controls.createContext("/identity") { exchange ->
            val name = exchange.requestURI.rawQuery?.removePrefix("account=")
            val allowed = name in users
            val payload = if (allowed) mapOf(
                "idToken" to FakeOidcProvider.idToken("roster-$name", nonce = "fictional-roster-nonce"),
                "nonce" to "fictional-roster-nonce",
            ) else emptyMap()
            val body = mapper.writeValueAsBytes(payload)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(if (allowed) 200 else 400, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        controls.createContext("/publish") { exchange ->
            // Only this explicitly opted-in test server has control endpoints; no production routes are added.
            val phase = exchange.requestURI.rawQuery?.removePrefix("phase=")
            var status = 200
            try {
                check(exchange.requestMethod == "POST" && phase in setOf("first", "second"))
                val actor = users.getValue("admin").toString()
                var view = roster.createRoster(ward, CreateRosterRequest(month.toString()), actor, UUID.randomUUID())
                roster.replaceRequirements(view.rosterId, tag(view), ReplaceRequirementsRequest(listOf(
                    StaffingRequirement(month.atDay(10), shift, 1, emptyList()),
                )), actor)
                view = roster.getRoster(view.rosterId, actor)
                view = roster.assign(view.rosterId, view.slots.single().slotId, tag(view), UUID.randomUUID(),
                    AssignNurseRequest(nurses.getValue(if (phase == "first") "member" else "limited")), actor)
                roster.confirm(view.rosterId, tag(view), UUID.randomUUID(), ConfirmRosterRequest(), actor)
            } catch (_: Exception) { status = 500 }
            exchange.sendResponseHeaders(status, -1)
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
            assertTrue(complete.await(10, TimeUnit.MINUTES), "Roster device test timed out")
            assertTrue(passed.get(), "Roster device test failed or did not complete")
        } finally { controls.stop(0) }
    }

    private fun tag(view: RosterView) = "\"${roster.etag(view.rosterId, view.revision)}\""

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
