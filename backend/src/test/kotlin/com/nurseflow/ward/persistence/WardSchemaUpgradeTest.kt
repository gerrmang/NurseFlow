package com.nurseflow.ward.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator
import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/** An H2 file DB created before A4 keeps its wards and gains the nullable hospital columns. */
class WardSchemaUpgradeTest {
    @Test
    fun `current schema applies on top of the pre-A4 ward tables`() {
        val dataSource = DriverManagerDataSource("jdbc:h2:mem:ward_upgrade_${UUID.randomUUID()};DB_CLOSE_DELAY=-1", "sa", "")
        val jdbc = JdbcTemplate(dataSource)
        jdbc.execute("CREATE TABLE wards (ward_id UUID PRIMARY KEY, name VARCHAR(100) NOT NULL, time_zone VARCHAR(100) NOT NULL)")
        jdbc.execute("CREATE TABLE bootstrap_state (state_id INT PRIMARY KEY, consumed BOOLEAN NOT NULL DEFAULT FALSE)")
        val legacy = UUID.randomUUID()
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", legacy, "가상 이전 병동", "Asia/Seoul")

        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)
        ResourceDatabasePopulator(ClassPathResource("schema.sql")).execute(dataSource)

        val row = jdbc.queryForMap("SELECT name, hospital_name, hospital_external_id, created_at FROM wards WHERE ward_id=?", legacy)
            .mapKeys { it.key.lowercase() }
        assertEquals("가상 이전 병동", row["name"])
        assertNull(row["hospital_name"])
        assertNull(row["hospital_external_id"])
        assertNull(row["created_at"])
    }
}
