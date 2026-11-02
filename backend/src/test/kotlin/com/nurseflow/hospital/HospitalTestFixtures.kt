package com.nurseflow.hospital

import org.springframework.jdbc.core.JdbcTemplate
import java.util.UUID

/** 합성 데이터만 쓰는 병원·병동 테스트 도우미. */
object HospitalTestFixtures {
    private val tables = listOf(
        "mutation_replays", "generation_jobs", "approved_absences", "roster_slots", "rosters",
        "shift_types", "nurses", "ward_memberships", "wards", "hospital_memberships", "hospitals",
    )

    fun reset(jdbc: JdbcTemplate) = tables.forEach { jdbc.update("DELETE FROM $it") }

    fun hospital(jdbc: JdbcTemplate, name: String, zone: String = "Asia/Seoul"): UUID = UUID.randomUUID().also {
        jdbc.update("INSERT INTO hospitals(hospital_id,name,time_zone,created_at) VALUES(?,?,?,?)",
            it, name, zone, "2026-11-01T00:00:00Z")
    }

    fun hospitalAdmin(jdbc: JdbcTemplate, hospitalId: UUID, subject: String) {
        jdbc.update("INSERT INTO hospital_memberships(hospital_id,subject,role,granted_at) VALUES(?,?,'HOSPITAL_ADMIN',?)",
            hospitalId, subject, "2026-11-01T00:00:00Z")
    }

    fun ward(jdbc: JdbcTemplate, hospitalId: UUID?, name: String): UUID = UUID.randomUUID().also {
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone,hospital_id) VALUES(?,?,?,?)", it, name, "Asia/Seoul", hospitalId)
    }

    fun wardRole(jdbc: JdbcTemplate, wardId: UUID, subject: String, role: String) {
        jdbc.update("INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,?,TRUE)",
            wardId, subject, role)
    }
}
