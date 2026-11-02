package com.nurseflow.hospital.api

import com.nurseflow.hospital.HospitalTestFixtures
import jakarta.servlet.Filter
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.oauth2.jwt.BadJwtException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.Instant
import java.util.UUID

/**
 * 실제 Spring Security 필터 체인과 MVC 오류 처리 경계를 확인한다. 테스트 전용 JwtDecoder는
 * `test-<subject>` 형식만 받아들인다. 운영 설정의 OIDC 검증이나 인증 우회는 바꾸지 않는다.
 * 병원과 최초 병원 관리자는 운영자 초기 설정 속성으로 등록된다.
 */
@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:hospital_controller;DB_CLOSE_DELAY=-1",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.invalid",
    "spring.security.oauth2.resourceserver.jwt.audiences=nurseflow-api",
    "nurseflow.bootstrap.hospital.name=부트스트랩 가상 병원",
    "nurseflow.bootstrap.hospital.admin-subject=boot-admin",
])
@Import(HospitalControllerTest.TestJwt::class)
class HospitalControllerTest @Autowired constructor(
    private val context: WebApplicationContext,
    private val jdbc: JdbcTemplate,
) {
    private lateinit var mvc: MockMvc
    private lateinit var hospitalId: UUID

    @TestConfiguration
    class TestJwt {
        @Bean
        fun jwtDecoder(): JwtDecoder = JwtDecoder { token ->
            if (!token.startsWith("test-")) throw BadJwtException("invalid test token")
            Jwt.withTokenValue(token).header("alg", "none").subject(token.removePrefix("test-"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build()
        }
    }

    @BeforeEach
    fun setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
            .addFilters<DefaultMockMvcBuilder>(context.getBean("springSecurityFilterChain", Filter::class.java))
            .build()
        listOf("mutation_replays", "ward_memberships", "wards").forEach { jdbc.update("DELETE FROM $it") }
        hospitalId = jdbc.queryForObject("SELECT hospital_id FROM hospitals WHERE name=?", UUID::class.java, "부트스트랩 가상 병원")!!
    }

    @Test
    fun `requests without a valid token are rejected by the security filter`() {
        mvc.perform(get("/api/v1/hospitals")).andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        mvc.perform(get("/api/v1/hospitals").header("Authorization", "Bearer forged"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `bootstrapped hospital admin sees the hospital`() {
        mvc.perform(get("/api/v1/hospitals").asUser("boot-admin"))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(jsonPath("$.items[0].hospitalId").value(hospitalId.toString()))
            .andExpect(jsonPath("$.items[0].roles[0]").value("HOSPITAL_ADMIN"))
        mvc.perform(get("/api/v1/hospitals").asUser("someone-else"))
            .andExpect(status().isOk).andExpect(jsonPath("$.items").isEmpty)
    }

    @Test
    fun `hospital admin creates, retries, lists and reads a ward`() {
        val key = UUID.randomUUID()
        val created = createWard("boot-admin", """{"name":"HTTP 병동"}""", key)
            .andExpect(status().isCreated)
            .andExpect(header().string("Location", startsWith("/api/v1/hospitals/$hospitalId/wards/")))
            .andExpect(jsonPath("$.name").value("HTTP 병동"))
            .andExpect(jsonPath("$.timeZone").value("Asia/Seoul"))
            .andReturn().response
        val wardId = Regex("\"wardId\":\"([0-9a-f-]+)\"").find(created.contentAsString)!!.groupValues[1]

        createWard("boot-admin", """{"name":"HTTP 병동"}""", key)
            .andExpect(status().isCreated).andExpect(jsonPath("$.wardId").value(wardId))
        createWard("boot-admin", """{"name":"HTTP 병동"}""", UUID.randomUUID())
            .andExpect(status().isConflict).andExpect(jsonPath("$.code").value("WARD_NAME_TAKEN"))
        mvc.perform(get("/api/v1/hospitals/$hospitalId/wards").asUser("boot-admin"))
            .andExpect(status().isOk).andExpect(jsonPath("$.items.length()").value(1))
        mvc.perform(get("/api/v1/hospitals/$hospitalId/wards/$wardId").asUser("boot-admin"))
            .andExpect(status().isOk).andExpect(jsonPath("$.wardId").value(wardId))
        mvc.perform(get("/api/v1/hospitals/$hospitalId/wards/${UUID.randomUUID()}").asUser("boot-admin"))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("WARD_NOT_FOUND"))
    }

    @Test
    fun `clients cannot supply roles or skip the idempotency key`() {
        createWard("boot-admin", """{"name":"권한 주입 병동","role":"HOSPITAL_ADMIN"}""", UUID.randomUUID())
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        createWard("boot-admin", """{"name":"관리자 지정 병동","adminSubject":"someone"}""", UUID.randomUUID())
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        mvc.perform(post("/api/v1/hospitals/$hospitalId/wards").asUser("boot-admin")
            .contentType(MediaType.APPLICATION_JSON).content("""{"name":"키 없는 병동"}"""))
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        createWard("boot-admin", """{"name":" "}""", UUID.randomUUID())
            .andExpect(status().isBadRequest).andExpect(jsonPath("$.code").value("INVALID_WARD_NAME"))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM wards", Int::class.java))
    }

    @Test
    fun `non admins get problem responses without creating wards`() {
        val existing = HospitalTestFixtures.ward(jdbc, hospitalId, "기존 병동")
        HospitalTestFixtures.wardRole(jdbc, existing, "ward-admin", "WARD_ADMIN")

        createWard("ward-admin", """{"name":"새 병동"}""", UUID.randomUUID())
            .andExpect(status().isForbidden)
            .andExpect(header().string("Content-Type", startsWith("application/problem+json")))
            .andExpect(jsonPath("$.code").value("HOSPITAL_ADMIN_REQUIRED"))
        createWard("stranger", """{"name":"새 병동"}""", UUID.randomUUID())
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("HOSPITAL_NOT_FOUND"))
        mvc.perform(get("/api/v1/hospitals/$hospitalId/wards").asUser("stranger"))
            .andExpect(status().isNotFound).andExpect(jsonPath("$.code").value("HOSPITAL_NOT_FOUND"))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM wards", Int::class.java))
    }

    @Test
    fun `ward members see only their own wards without gaining hospital access`() {
        createWard("boot-admin", """{"name":"관리 병동"}""", UUID.randomUUID()).andExpect(status().isCreated)
        val memberWard = HospitalTestFixtures.ward(jdbc, hospitalId, "간호사 병동")
        HospitalTestFixtures.wardRole(jdbc, memberWard, "ward-nurse", "WARD_MEMBER")
        val legacy = HospitalTestFixtures.ward(jdbc, null, "병원 미지정 병동")
        HospitalTestFixtures.wardRole(jdbc, legacy, "ward-nurse", "WARD_MEMBER")

        mvc.perform(get("/api/v1/wards").asUser("ward-nurse"))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "private, no-store"))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[?(@.wardName=='간호사 병동')].hospitalName").value("부트스트랩 가상 병원"))
            .andExpect(jsonPath("$.items[?(@.wardName=='간호사 병동')].roles[0]").value("WARD_MEMBER"))
            .andExpect(jsonPath("$.items[?(@.wardName=='병원 미지정 병동')].hospitalId").value(org.hamcrest.Matchers.contains(null as Any?)))
            .andExpect(jsonPath("$.items[?(@.wardName=='관리 병동')]").isEmpty)
        mvc.perform(get("/api/v1/hospitals").asUser("ward-nurse"))
            .andExpect(status().isOk).andExpect(jsonPath("$.items").isEmpty)
        mvc.perform(get("/api/v1/hospitals/$hospitalId/wards").asUser("ward-nurse"))
            .andExpect(status().isForbidden).andExpect(jsonPath("$.code").value("HOSPITAL_ADMIN_REQUIRED"))

        // 병동을 만든 병원 관리자에게는 생성 시 부여된 병동 관리자 소속만 보인다.
        mvc.perform(get("/api/v1/wards").asUser("boot-admin"))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].wardName").value("관리 병동"))
            .andExpect(jsonPath("$.items[0].roles[0]").value("WARD_ADMIN"))
            .andExpect(jsonPath("$.items[0].canViewWardCalendar").value(true))
        mvc.perform(get("/api/v1/wards").asUser("stranger"))
            .andExpect(status().isOk).andExpect(jsonPath("$.items").isEmpty)
        mvc.perform(get("/api/v1/wards")).andExpect(status().isUnauthorized)
    }

    private fun createWard(subject: String, body: String, key: UUID): ResultActions = mvc.perform(
        post("/api/v1/hospitals/$hospitalId/wards").asUser(subject)
            .header("Idempotency-Key", key.toString())
            .contentType(MediaType.APPLICATION_JSON).content(body),
    )

    private fun MockHttpServletRequestBuilder.asUser(subject: String) = header("Authorization", "Bearer test-$subject")
}
