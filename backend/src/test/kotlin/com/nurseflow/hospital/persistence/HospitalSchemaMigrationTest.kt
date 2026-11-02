package com.nurseflow.hospital.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.nio.file.Path
import java.util.UUID

/**
 * 병원 기반 이전에 만들어진 H2 **파일** DB에 현재 `schema.sql`을 반복 적용해도
 * 기존 병동·소속·근무표가 보존되고 새 열·테이블·제약이 생기는지 확인한다.
 */
class HospitalSchemaMigrationTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `current schema upgrades a legacy file database in place and is repeatable`() {
        val jdbc = JdbcTemplate(DriverManagerDataSource("jdbc:h2:file:${dir.resolve("legacy")}", "sa", ""))
        apply(jdbc, "db/legacy/schema-v0-before-hospital.sql")
        val legacyWard = UUID.randomUUID()
        val roster = UUID.randomUUID()
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", legacyWard, "기존 병동", "Asia/Seoul")
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,'WARD_ADMIN',TRUE)",
            legacyWard, "legacy-admin")
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,'2026-11','DRAFT',FALSE,3)",
            roster, legacyWard)

        apply(jdbc, "schema.sql")
        apply(jdbc, "schema.sql")

        assertEquals(setOf("HOSPITAL_ID", "CREATED_AT", "CREATED_BY"), jdbc.queryForList(
            "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME='WARDS' AND COLUMN_NAME IN ('HOSPITAL_ID','CREATED_AT','CREATED_BY')",
            String::class.java,
        ).toSet())
        val row = jdbc.queryForMap("SELECT name, hospital_id FROM wards WHERE ward_id=?", legacyWard)
        assertEquals("기존 병동", row["NAME"])
        assertNull(row["HOSPITAL_ID"])
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM ward_memberships WHERE ward_id=?", Int::class.java, legacyWard))
        assertEquals(3, jdbc.queryForObject("SELECT revision FROM rosters WHERE roster_id=?", Int::class.java, roster))
        assertEquals(1, jdbc.queryForObject(
            "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS WHERE CONSTRAINT_NAME='FK_WARDS_HOSPITAL'", Int::class.java))

        // 새 제약이 실제로 동작한다: 없는 병원 참조와 같은 병원의 중복 병동 이름은 거부된다.
        assertThrows(DataIntegrityViolationException::class.java) {
            jdbc.update("INSERT INTO wards(ward_id,name,time_zone,hospital_id) VALUES(?,?,?,?)",
                UUID.randomUUID(), "고아 병동", "Asia/Seoul", UUID.randomUUID())
        }
        val hospital = UUID.randomUUID()
        jdbc.update("INSERT INTO hospitals(hospital_id,name,time_zone,created_at) VALUES(?,?,?,?)",
            hospital, "가상 병원", "Asia/Seoul", "2026-11-02T00:00:00Z")
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone,hospital_id) VALUES(?,?,?,?)", UUID.randomUUID(), "내과", "Asia/Seoul", hospital)
        assertThrows(DataIntegrityViolationException::class.java) {
            jdbc.update("INSERT INTO wards(ward_id,name,time_zone,hospital_id) VALUES(?,?,?,?)", UUID.randomUUID(), "내과", "Asia/Seoul", hospital)
        }
        assertThrows(DataIntegrityViolationException::class.java) {
            jdbc.update("INSERT INTO hospital_memberships(hospital_id,subject,role,granted_at) VALUES(?,?,'WARD_ADMIN',?)",
                hospital, "someone", "2026-11-02T00:00:00Z")
        }
        jdbc.execute("SHUTDOWN")
    }

    private fun apply(jdbc: JdbcTemplate, resource: String) {
        ResourceDatabasePopulator(ClassPathResource(resource)).execute(jdbc.dataSource!!)
    }
}
