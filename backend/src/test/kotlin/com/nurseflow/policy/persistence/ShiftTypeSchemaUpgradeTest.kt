package com.nurseflow.policy.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.nio.file.Path
import java.util.UUID

/** An H2 file DB created before A8 keeps its shift types and rosters and gains the new columns with defaults. */
class ShiftTypeSchemaUpgradeTest {
    @TempDir lateinit var dir: Path

    @Test
    fun `current schema applies on top of a pre-A8 shift_types and rosters`() {
        val url = "jdbc:h2:file:${dir.resolve("nurseflow")};DB_CLOSE_ON_EXIT=FALSE"
        val ward = UUID.randomUUID()
        val shift = UUID.randomUUID()
        val roster = UUID.randomUUID()
        DriverManagerDataSource(url, "sa", "").let { old ->
            val jdbc = JdbcTemplate(old)
            jdbc.execute("CREATE TABLE wards (ward_id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, time_zone VARCHAR(100) NOT NULL)")
            jdbc.execute(
                """CREATE TABLE shift_types (shift_type_id UUID PRIMARY KEY, ward_id UUID NOT NULL REFERENCES wards(ward_id),
                   code VARCHAR(32) NOT NULL, display_name VARCHAR(64) NOT NULL,
                   kind VARCHAR(10) NOT NULL CHECK (kind IN ('DAY', 'NIGHT', 'OTHER')),
                   start_time TIME NOT NULL, end_time TIME NOT NULL, spans_midnight BOOLEAN NOT NULL,
                   active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE (ward_id, code))""",
            )
            jdbc.execute(
                """CREATE TABLE rosters (roster_id UUID PRIMARY KEY, ward_id UUID NOT NULL REFERENCES wards(ward_id),
                   roster_month VARCHAR(7) NOT NULL, status VARCHAR(10) NOT NULL, active BOOLEAN NOT NULL DEFAULT FALSE,
                   revision INT NOT NULL, published_at VARCHAR(40), confirmed_by VARCHAR(255))""",
            )
            jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", ward, "가상 이전 병동", "Asia/Seoul")
            jdbc.update(
                "INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight) VALUES(?,?,?,?,?,?,?,?)",
                shift, ward, "N", "나이트", "NIGHT", "22:00:00", "07:00:00", true,
            )
            jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,?,?,?)",
                roster, ward, "2026-11", "DRAFT", false, 3)
            jdbc.execute("SHUTDOWN")
        }

        val dataSource = DriverManagerDataSource(url, "sa", "")
        val jdbc = JdbcTemplate(dataSource)
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)

        val type = jdbc.queryForMap(
            "SELECT code,display_name,kind,spans_midnight,active,break_minutes,recognized_minutes,target_credit_minutes FROM shift_types WHERE shift_type_id=?",
            shift,
        ).mapKeys { it.key.lowercase() }
        assertEquals("N", type["code"])
        assertEquals("나이트", type["display_name"])
        assertEquals(true, type["spans_midnight"])
        assertEquals(true, type["active"])
        assertEquals(0, type["break_minutes"])
        assertNull(type["recognized_minutes"])
        assertNull(type["target_credit_minutes"])

        val row = jdbc.queryForMap("SELECT revision, needs_revalidation FROM rosters WHERE roster_id=?", roster)
            .mapKeys { it.key.lowercase() }
        assertEquals(3, row["revision"])
        assertEquals(false, row["needs_revalidation"])
        assertFalse(jdbc.queryForList("SELECT revision FROM ward_policy_revisions").isNotEmpty())
        jdbc.execute("SHUTDOWN")
    }
}
