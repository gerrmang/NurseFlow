package com.nurseflow.staff.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import java.nio.file.Path
import java.util.UUID

/** An H2 file DB from before A5 keeps its nurses; they get no team position, not senior, no stage override. */
class StaffSchemaUpgradeTest {
    @TempDir lateinit var dir: Path

    @Test
    fun `current schema applies on top of a pre-A5 H2 file database`() {
        val url = "jdbc:h2:file:${dir.resolve("nurseflow")};DB_CLOSE_ON_EXIT=FALSE"
        val ward = UUID.randomUUID()
        val nurse = UUID.randomUUID()
        JdbcTemplate(DriverManagerDataSource(url, "sa", "")).let { old ->
            old.execute("CREATE TABLE wards (ward_id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, time_zone VARCHAR(100) NOT NULL)")
            old.execute(
                """CREATE TABLE nurses (nurse_id UUID PRIMARY KEY, ward_id UUID NOT NULL REFERENCES wards(ward_id),
                   subject VARCHAR(255), display_name VARCHAR(100) NOT NULL, qualifications VARCHAR(2000) NOT NULL DEFAULT '[]',
                   active BOOLEAN NOT NULL DEFAULT TRUE, UNIQUE (ward_id, subject))""",
            )
            old.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", ward, "가상 병동", "Asia/Seoul")
            old.update("INSERT INTO nurses(nurse_id,ward_id,display_name) VALUES(?,?,?)", nurse, ward, "가상 간호사")
            old.execute("SHUTDOWN")
        }

        val dataSource = DriverManagerDataSource(url, "sa", "")
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)

        val jdbc = JdbcTemplate(dataSource)
        val row = jdbc.queryForMap("SELECT display_name, team_order, senior_eligible, stage_override FROM nurses WHERE nurse_id=?", nurse)
            .mapKeys { it.key.lowercase() }
        assertEquals("가상 간호사", row["display_name"])
        assertNull(row["team_order"])
        assertEquals(false, row["senior_eligible"])
        assertNull(row["stage_override"])
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM staff_adjustments", Int::class.java))
        jdbc.execute("SHUTDOWN")
    }
}
