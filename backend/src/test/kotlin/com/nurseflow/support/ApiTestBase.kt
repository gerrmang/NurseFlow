package com.nurseflow.support

import com.nurseflow.auth.application.service.SessionTokenService
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

/**
 * Full HTTP stack (servlet + security filters + H2) with the fake Kakao provider.
 * Each subclass gets its own in-memory database through [databaseName].
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class ApiTestBase {
    @Autowired protected lateinit var jdbc: JdbcTemplate
    @Autowired protected lateinit var mapper: JsonMapper
    @Autowired protected lateinit var sessionTokens: SessionTokenService
    @LocalServerPort protected var port: Int = 0
    protected lateinit var http: HttpTestClient

    @BeforeEach
    fun resetDatabase() {
        http = HttpTestClient(port, mapper)
        listOf("hospitals", "hospital_dataset_imports", "staff_adjustments", "audit_events", "auth_web_login_attempts", "ward_invitations", "ward_career_policies", "auth_refresh_tokens", "mutation_replays", "generation_jobs", "approved_absences",
            "roster_slots", "rosters", "shift_types", "nurses", "ward_teams", "ward_policy_revisions", "ward_memberships", "users", "wards",
        ).forEach { table -> runCatching { jdbc.update("DELETE FROM $table") } }
    }

    /** Creates a user row and returns a valid NurseFlow access token for it. */
    protected fun userToken(name: String = "가상 사용자"): Pair<UUID, String> {
        val id = UUID.randomUUID()
        jdbc.update(
            "INSERT INTO users(user_id,provider,provider_subject,display_name,created_at,last_login_at) VALUES(?,?,?,?,?,?)",
            id, "KAKAO", "fake-$id", name, Instant.now().toString(), Instant.now().toString(),
        )
        return id to sessionTokens.issueAccessToken(id, UUID.randomUUID()).value
    }

    protected fun insertWard(name: String = "가상 병동"): UUID = UUID.randomUUID().also {
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", it, name, "Asia/Seoul")
    }

    protected fun grant(wardId: UUID, userId: UUID, role: String, canView: Boolean = true) {
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,?,?)",
            wardId, userId.toString(), role, canView)
    }

    protected fun insertNurse(wardId: UUID, name: String = "가상 간호사", subject: String? = null, active: Boolean = true): UUID =
        UUID.randomUUID().also {
            jdbc.update("INSERT INTO nurses(nurse_id,ward_id,subject,display_name,qualifications,active) VALUES(?,?,?,?,'[]',?)",
                it, wardId, subject, name, active)
        }

    protected fun idem() = mapOf("Idempotency-Key" to UUID.randomUUID().toString())

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun fakeKakao(registry: DynamicPropertyRegistry) {
            registry.add("nurseflow.auth.kakao.issuer") { FakeOidcProvider.ISSUER }
            registry.add("nurseflow.auth.kakao.jwk-set-uri") { FakeOidcProvider.jwkSetUri }
            registry.add("nurseflow.auth.kakao.token-uri") { FakeOidcProvider.tokenUri }
            registry.add("nurseflow.auth.kakao.authorize-uri") { FakeOidcProvider.AUTHORIZE_URI }
            registry.add("nurseflow.hospital.hira.base-uri") { FakeHiraServer.baseUri }
            registry.add("spring.datasource.url") { "jdbc:h2:mem:api_tests;DB_CLOSE_DELAY=-1" }
        }
    }
}
