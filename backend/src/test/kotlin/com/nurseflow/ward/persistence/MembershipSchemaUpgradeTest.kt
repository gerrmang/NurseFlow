package com.nurseflow.ward.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.nio.file.Path
import java.util.UUID

/** An H2 file DB from before A6 keeps its memberships; they become active members after the upgrade. */
class MembershipSchemaUpgradeTest {
    @TempDir lateinit var dir: Path

    @Test
    fun `existing memberships stay and are active after the schema is applied twice`() {
        val url = "jdbc:h2:file:${dir.resolve("nurseflow")};DB_CLOSE_ON_EXIT=FALSE"
        val ward = UUID.randomUUID()
        DriverManagerDataSource(url, "sa", "").let { old ->
            val jdbc = JdbcTemplate(old)
            jdbc.execute("CREATE TABLE wards (ward_id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, time_zone VARCHAR(100) NOT NULL)")
            jdbc.execute(
                """CREATE TABLE ward_memberships (
                    ward_id UUID NOT NULL REFERENCES wards(ward_id),
                    subject VARCHAR(255) NOT NULL,
                    role VARCHAR(20) NOT NULL CHECK (role IN ('WARD_ADMIN', 'WARD_MEMBER')),
                    can_view_ward_calendar BOOLEAN NOT NULL DEFAULT FALSE,
                    PRIMARY KEY (ward_id, subject, role))""",
            )
            jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", ward, "가상 이전 병동", "Asia/Seoul")
            jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,?,?)", ward, "fake-subject", "WARD_ADMIN", true)
            jdbc.execute("SHUTDOWN")
        }

        val dataSource = DriverManagerDataSource(url, "sa", "")
        val jdbc = JdbcTemplate(dataSource)
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)

        val rows = jdbc.queryForList("SELECT subject, role, active FROM ward_memberships WHERE ward_id=?", ward)
            .map { r -> r.mapKeys { it.key.lowercase() } }
        assertEquals(1, rows.size)
        assertEquals("fake-subject", rows[0]["subject"])
        assertEquals("WARD_ADMIN", rows[0]["role"])
        assertTrue(rows[0]["active"] as Boolean)
        jdbc.execute("SHUTDOWN")
    }
}
