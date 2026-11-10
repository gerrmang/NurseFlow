package com.nurseflow.ward.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.jdbc.core.JdbcTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * An H2 file DB created before A4 (like the default `./nurseflow` file) keeps its wards and gains the
 * nullable hospital columns. The file is closed and reopened between the old and the new schema, as on an upgrade.
 */
class WardSchemaUpgradeTest {
    @TempDir lateinit var dir: Path

    @Test
    fun `current schema applies on top of a pre-A4 H2 file database`() {
        val url = "jdbc:h2:file:${dir.resolve("nurseflow")};DB_CLOSE_ON_EXIT=FALSE"
        val legacy = UUID.randomUUID()
        DriverManagerDataSource(url, "sa", "").let { old ->
            val jdbc = JdbcTemplate(old)
            jdbc.execute("CREATE TABLE wards (ward_id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, time_zone VARCHAR(100) NOT NULL)")
            jdbc.execute("CREATE TABLE bootstrap_state (state_id INT PRIMARY KEY, consumed BOOLEAN NOT NULL DEFAULT FALSE)")
            jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", legacy, "가상 이전 병동", "Asia/Seoul")
            jdbc.execute("SHUTDOWN")
        }
        assertTrue(Files.exists(dir.resolve("nurseflow.mv.db")))

        val dataSource = DriverManagerDataSource(url, "sa", "")
        val jdbc = JdbcTemplate(dataSource)
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)

        val row = jdbc.queryForMap("SELECT name, hospital_name, hospital_external_id, created_at FROM wards WHERE ward_id=?", legacy)
            .mapKeys { it.key.lowercase() }
        assertEquals("가상 이전 병동", row["name"])
        assertNull(row["hospital_name"])
        assertNull(row["hospital_external_id"])
        assertNull(row["created_at"])
        // The unused bootstrap table of the old database is left as it was.
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM bootstrap_state", Int::class.java))
        jdbc.execute("SHUTDOWN")
    }
}
