package com.nurseflow.roster.persistence.migration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import java.sql.DriverManager

class ShiftKindConstraintMigrationTest {
    @Test
    fun `old H2 data remains and Evening can be inserted after migration`() {
        val connection = DriverManager.getConnection("jdbc:h2:mem:shift_kind_migration;DB_CLOSE_DELAY=-1")
        val jdbc = JdbcTemplate(SingleConnectionDataSource(connection, true))
        jdbc.execute("""CREATE TABLE shift_types (
            shift_type_id INT PRIMARY KEY,
            kind VARCHAR(10) NOT NULL CHECK (kind IN ('DAY', 'NIGHT', 'OTHER'))
        )""")
        jdbc.update("INSERT INTO shift_types(shift_type_id, kind) VALUES (1, 'DAY')")

        val migration = ShiftKindConstraintMigration(jdbc)
        migration.migrate()
        migration.migrate()
        jdbc.update("INSERT INTO shift_types(shift_type_id, kind) VALUES (2, 'EVENING')")

        assertEquals(listOf("DAY", "EVENING"), jdbc.queryForList(
            "SELECT kind FROM shift_types ORDER BY shift_type_id", String::class.java,
        ))
    }
}
