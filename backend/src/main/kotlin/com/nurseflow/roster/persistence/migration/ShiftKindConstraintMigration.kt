package com.nurseflow.roster.persistence.migration

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/** Extends the original H2 check constraint without replacing the shift_types table or its rows. */
@Component
class ShiftKindConstraintMigration(private val jdbc: JdbcTemplate) : ApplicationRunner {
    override fun run(args: ApplicationArguments) = migrate()

    internal fun migrate() {
        val checks = jdbc.query(
            """SELECT tc.CONSTRAINT_NAME, cc.CHECK_CLAUSE
               FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
               JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                 ON cc.CONSTRAINT_CATALOG = tc.CONSTRAINT_CATALOG
                AND cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
                AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
               WHERE tc.TABLE_NAME = 'SHIFT_TYPES' AND tc.CONSTRAINT_TYPE = 'CHECK'""",
        ) { rs, _ -> rs.getString(1) to rs.getString(2) }

        val old = checks.filter { (_, clause) ->
            val normalized = clause.uppercase()
            normalized.contains("KIND") && normalized.contains("NIGHT") &&
                normalized.contains("OTHER") && !normalized.contains("EVENING")
        }
        if (old.isEmpty()) return
        check(old.size == 1) { "근무 종류의 기존 제약을 하나로 식별할 수 없습니다." }

        if (checks.none { it.first == NEW_CONSTRAINT }) {
            jdbc.execute(
                "ALTER TABLE shift_types ADD CONSTRAINT $NEW_CONSTRAINT " +
                    "CHECK (kind IN ('DAY', 'EVENING', 'NIGHT', 'OTHER'))",
            )
        }
        val oldName = old.single().first
        check(oldName.matches(Regex("[A-Za-z0-9_]+"))) { "근무 종류 제약의 이름이 올바르지 않습니다." }
        jdbc.execute("ALTER TABLE shift_types DROP CONSTRAINT \"$oldName\"")
    }

    private companion object {
        const val NEW_CONSTRAINT = "CK_SHIFT_TYPES_KIND_V2"
    }
}
