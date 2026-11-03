package com.nurseflow.hospital

import com.nurseflow.common.error.BusinessException
import com.nurseflow.hospital.application.model.CreateWardCommand
import com.nurseflow.hospital.application.service.HospitalWardService
import com.nurseflow.roster.application.model.CreateRosterRequest
import com.nurseflow.roster.application.service.RosterService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(properties = [
    "spring.datasource.url=jdbc:h2:mem:hospital_ward_service;DB_CLOSE_DELAY=-1",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.invalid",
    "spring.security.oauth2.resourceserver.jwt.audiences=nurseflow-api",
])
class HospitalWardServiceIntegrationTest @Autowired constructor(
    private val jdbc: JdbcTemplate,
    private val service: HospitalWardService,
    private val rosters: RosterService,
) {
    private val admin = "hospital-admin-a"
    private lateinit var hospitalA: UUID
    private lateinit var hospitalB: UUID

    @BeforeEach
    fun setup() {
        HospitalTestFixtures.reset(jdbc)
        hospitalA = HospitalTestFixtures.hospital(jdbc, "가상 병원 A")
        hospitalB = HospitalTestFixtures.hospital(jdbc, "가상 병원 B", "UTC")
        HospitalTestFixtures.hospitalAdmin(jdbc, hospitalA, admin)
        HospitalTestFixtures.hospitalAdmin(jdbc, hospitalB, "hospital-admin-b")
    }

    @Test
    fun `hospital admin creates a ward and becomes its first ward admin in one step`() {
        val ward = service.createWard(admin, command(hospitalA, "  내과 병동  "))

        assertEquals("내과 병동", ward.name)
        assertEquals("Asia/Seoul", ward.timeZone)
        assertEquals(hospitalA, ward.hospitalId)
        assertEquals(listOf("WARD_ADMIN"), jdbc.queryForList(
            "SELECT role FROM ward_memberships WHERE ward_id=? AND subject=?", String::class.java, ward.wardId, admin))
        assertEquals(admin, jdbc.queryForObject("SELECT created_by FROM wards WHERE ward_id=?", String::class.java, ward.wardId))
        assertEquals(listOf(ward), service.listWards(admin, hospitalA))
        assertEquals(ward, service.getWard(admin, hospitalA, ward.wardId))

        // 기존 근무표 권한 검사는 새 병동 관리자 소속을 그대로 사용한다.
        val draft = rosters.createRoster(ward.wardId, CreateRosterRequest("2026-12"), admin, UUID.randomUUID())
        assertEquals("DRAFT", draft.status)
        assertEquals(1, rosters.listRosters(ward.wardId, "2026-12", admin).size)
    }

    @Test
    fun `ward time zone may be overridden but must be valid`() {
        assertEquals("UTC", service.createWard(admin, command(hospitalA, "외과", "UTC")).timeZone)
        assertCode("INVALID_TIME_ZONE") { service.createWard(admin, command(hospitalA, "소아과", "Mars/Base")) }
        assertCode("INVALID_TIME_ZONE") { service.createWard(admin, command(hospitalA, "정형외과", "+09:00")) }
        listOf("", "   ", "가".repeat(101), "줄\n바꿈").forEach { name ->
            assertCode("INVALID_WARD_NAME") { service.createWard(admin, command(hospitalA, name)) }
        }
        assertEquals(1, wardCount(hospitalA))
    }

    @Test
    fun `users without hospital admin role cannot create or list wards`() {
        val existing = HospitalTestFixtures.ward(jdbc, hospitalA, "기존 병동")
        HospitalTestFixtures.wardRole(jdbc, existing, "ward-admin", "WARD_ADMIN")
        HospitalTestFixtures.wardRole(jdbc, existing, "ward-nurse", "WARD_MEMBER")

        // 병원 소속 병동의 관리자·간호사는 병원 존재를 알지만 병원 권한은 없다.
        listOf("ward-admin", "ward-nurse").forEach { subject ->
            assertCode("HOSPITAL_ADMIN_REQUIRED") { service.createWard(subject, command(hospitalA, "새 병동")) }
            assertCode("HOSPITAL_ADMIN_REQUIRED") { service.listWards(subject, hospitalA) }
            assertCode("HOSPITAL_ADMIN_REQUIRED") { service.getWard(subject, hospitalA, existing) }
            assertEquals(emptyList<Any>(), service.listMyHospitals(subject))
        }
        // 소속이 없는 로그인 사용자와 존재하지 않는 병원은 구분되지 않는다.
        assertCode("HOSPITAL_NOT_FOUND") { service.createWard("stranger", command(hospitalA, "새 병동")) }
        assertCode("HOSPITAL_NOT_FOUND") { service.createWard(admin, command(UUID.randomUUID(), "새 병동")) }
        assertEquals(1, wardCount(hospitalA))
    }

    @Test
    fun `hospital and ward boundaries are not crossed`() {
        val wardOfB = service.createWard("hospital-admin-b", command(hospitalB, "B 병동"))
        val legacyWard = HospitalTestFixtures.ward(jdbc, null, "병원 미지정 병동")

        assertCode("HOSPITAL_NOT_FOUND") { service.createWard(admin, command(hospitalB, "침투 병동")) }
        assertCode("HOSPITAL_NOT_FOUND") { service.listWards(admin, hospitalB) }
        assertCode("WARD_NOT_FOUND") { service.getWard(admin, hospitalA, wardOfB.wardId) }
        assertCode("WARD_NOT_FOUND") { service.getWard(admin, hospitalA, legacyWard) }
        assertEquals(listOf(hospitalA), service.listMyHospitals(admin).map { it.hospitalId })

        // 병원 관리자 역할도, 자기가 만든 병동의 병동 관리자 역할도 다른 병동의 근무표 권한으로 확장되지 않는다.
        val wardOfA = service.createWard(admin, command(hospitalA, "A 병동"))
        assertRosterDenied(wardOfB.wardId, admin)
        assertRosterDenied(legacyWard, admin)
        HospitalTestFixtures.wardRole(jdbc, wardOfA.wardId, "ward-admin-a", "WARD_ADMIN")
        val sibling = service.createWard(admin, command(hospitalA, "A 형제 병동"))
        assertRosterDenied(sibling.wardId, "ward-admin-a")
        assertCode("HOSPITAL_ADMIN_REQUIRED") { service.createWard("ward-admin-a", command(hospitalA, "권한 확장 시도")) }
    }

    @Test
    fun `failure while assigning the first ward admin rolls back the ward`() {
        val blocked = "blocked-admin"
        HospitalTestFixtures.hospitalAdmin(jdbc, hospitalA, blocked)
        jdbc.execute("ALTER TABLE ward_memberships ADD CONSTRAINT test_block_first_admin CHECK (subject <> '$blocked')")
        try {
            val key = UUID.randomUUID()
            assertThrows(DataIntegrityViolationException::class.java) {
                service.createWard(blocked, command(hospitalA, "롤백 병동", key = key))
            }
            assertEquals(0, wardCount(hospitalA))
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM mutation_replays WHERE idempotency_key=?", Int::class.java, key))
        } finally {
            jdbc.execute("ALTER TABLE ward_memberships DROP CONSTRAINT test_block_first_admin")
        }
        // 실패 후 같은 이름으로 다시 만들 수 있다.
        assertEquals("롤백 병동", service.createWard(admin, command(hospitalA, "롤백 병동")).name)
    }

    @Test
    fun `idempotent retry returns the first ward and key reuse is rejected`() {
        val key = UUID.randomUUID()
        val first = service.createWard(admin, command(hospitalA, "멱등 병동", key = key))
        val retry = service.createWard(admin, command(hospitalA, "멱등 병동", key = key))

        assertEquals(first, retry)
        assertEquals(1, wardCount(hospitalA))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ward_memberships WHERE ward_id=?", Int::class.java, first.wardId))
        assertCode("IDEMPOTENCY_KEY_REUSED") { service.createWard(admin, command(hospitalA, "다른 병동", key = key)) }
        HospitalTestFixtures.hospitalAdmin(jdbc, hospitalA, "hospital-admin-a2")
        assertCode("IDEMPOTENCY_KEY_REUSED") { service.createWard("hospital-admin-a2", command(hospitalA, "멱등 병동", key = key)) }
        assertCode("WARD_NAME_TAKEN") { service.createWard(admin, command(hospitalA, "멱등 병동")) }
        // 다른 병원은 같은 병동 이름을 쓸 수 있다.
        assertEquals("멱등 병동", service.createWard("hospital-admin-b", command(hospitalB, "멱등 병동")).name)
    }

    @Test
    fun `concurrent creates with distinct keys produce exactly one ward per name`() {
        val results = concurrently(8) { service.createWard(admin, command(hospitalA, "동시 병동")) }

        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.filter { it.isFailure }.all { (it.exceptionOrNull() as? BusinessException)?.errorCode?.name == "WARD_NAME_TAKEN" })
        assertEquals(1, wardCount(hospitalA))
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ward_memberships", Int::class.java))
    }

    @Test
    fun `concurrent retries with one key create one ward and return the same result`() {
        val key = UUID.randomUUID()
        val results = concurrently(8) { service.createWard(admin, command(hospitalA, "재시도 병동", key = key)) }

        assertTrue(results.all { it.isSuccess }, results.mapNotNull { it.exceptionOrNull() }.toString())
        assertEquals(1, results.map { it.getOrThrow().wardId }.toSet().size)
        assertEquals(1, wardCount(hospitalA))
    }

    private fun command(hospitalId: UUID, name: String?, zone: String? = null, key: UUID = UUID.randomUUID()) =
        CreateWardCommand(hospitalId, name, zone, key)

    private fun wardCount(hospitalId: UUID): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM wards WHERE hospital_id=?", Int::class.java, hospitalId)!!

    private fun assertRosterDenied(wardId: UUID, subject: String) {
        assertCode("WARD_NOT_FOUND") { rosters.listRosters(wardId, "2026-12", subject) }
    }

    private fun assertCode(code: String, block: () -> Unit) {
        assertEquals(code, assertThrows(BusinessException::class.java) { block() }.errorCode.name)
    }

    private fun <T> concurrently(threads: Int, task: () -> T): List<Result<T>> {
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        try {
            val futures = List(threads) { pool.submit(Callable { start.await(); runCatching(task) }) }
            start.countDown()
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }
}
