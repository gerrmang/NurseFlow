package com.nurseflow.hospital

import com.nurseflow.hospital.application.model.BootstrapHospitalCommand
import com.nurseflow.hospital.application.service.HospitalBootstrapService
import com.nurseflow.hospital.application.service.HospitalWardService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:hospital_bootstrap;DB_CLOSE_DELAY=-1",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.invalid",
    "spring.security.oauth2.resourceserver.jwt.audiences=nurseflow-api",
])
class HospitalBootstrapServiceTest @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val bootstrap: HospitalBootstrapService,
    private val wards: HospitalWardService,
) {
    @BeforeEach
    fun setup() = HospitalTestFixtures.reset(jdbc)

    @Test
    fun `bootstrap registers the hospital and its first admin once`() {
        val first = bootstrap.bootstrap(BootstrapHospitalCommand("가상 병원", "Asia/Seoul", "first-admin"))
        val again = bootstrap.bootstrap(BootstrapHospitalCommand(" 가상 병원 ", "Asia/Seoul", "first-admin"))

        assertTrue(first.created)
        assertFalse(again.created)
        assertEquals(first.hospitalId, again.hospitalId)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM hospitals", Int::class.java))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM hospital_memberships", Int::class.java))
        assertEquals(listOf("HOSPITAL_ADMIN"), wards.listMyHospitals("first-admin").single().roles)
    }

    @Test
    fun `bootstrap does not grant admin on an existing hospital to another subject`() {
        bootstrap.bootstrap(BootstrapHospitalCommand("가상 병원", "Asia/Seoul", "first-admin"))

        assertThrows(IllegalStateException::class.java) {
            bootstrap.bootstrap(BootstrapHospitalCommand("가상 병원", "Asia/Seoul", "intruder"))
        }
        assertEquals(emptyList<Any>(), wards.listMyHospitals("intruder"))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM hospital_memberships", Int::class.java))
    }

    @Test
    fun `bootstrap rejects incomplete settings without partial rows`() {
        listOf(
            BootstrapHospitalCommand("", "Asia/Seoul", "admin"),
            BootstrapHospitalCommand("가상 병원", "Not/AZone", "admin"),
            BootstrapHospitalCommand("가상 병원", "Asia/Seoul", " "),
            BootstrapHospitalCommand("가상 병원", "Asia/Seoul", null),
        ).forEach { command ->
            assertThrows(IllegalArgumentException::class.java) { bootstrap.bootstrap(command) }
        }
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM hospitals", Int::class.java))
    }
}
