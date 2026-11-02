package com.nurseflow.ward.persistence.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

data class DbMembershipRow(val wardId: UUID, val wardName: String, val role: String, val canViewWardCalendar: Boolean)

@Repository
class WardJdbcRepository(private val jdbc: JdbcTemplate) {
    fun membershipsOf(subject: String): List<DbMembershipRow> = jdbc.query(
        """SELECT m.ward_id, w.name, m.role, m.can_view_ward_calendar
           FROM ward_memberships m JOIN wards w ON w.ward_id = m.ward_id
           WHERE m.subject=? ORDER BY w.name, m.ward_id, m.role""",
        { rs, _ ->
            DbMembershipRow(
                rs.getObject("ward_id", UUID::class.java), rs.getString("name"),
                rs.getString("role"), rs.getBoolean("can_view_ward_calendar"),
            )
        },
        subject,
    )

    fun rolesIn(wardId: UUID, subject: String): Set<String> = jdbc.query(
        "SELECT role FROM ward_memberships WHERE ward_id=? AND subject=?",
        { rs, _ -> rs.getString("role") }, wardId, subject,
    ).toSet()

    fun findWardName(wardId: UUID): String? = jdbc.query(
        "SELECT name FROM wards WHERE ward_id=?", { rs, _ -> rs.getString("name") }, wardId,
    ).firstOrNull()

    /** Serializes membership changes in one ward (same lock as roster mutations). */
    fun lockWard(wardId: UUID): Boolean =
        jdbc.query("SELECT ward_id FROM wards WHERE ward_id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, wardId)
            .isNotEmpty()

    fun insertWard(wardId: UUID, name: String, timeZone: String) {
        jdbc.update("INSERT INTO wards(ward_id,name,time_zone) VALUES(?,?,?)", wardId, name, timeZone)
    }

    /** Admin's own career start and rank (admins have no nurse record). */
    fun setMembershipProfile(wardId: UUID, subject: String, role: String, careerStartedOn: String, rank: String) {
        jdbc.update(
            "UPDATE ward_memberships SET career_started_on=?, rank_name=? WHERE ward_id=? AND subject=? AND role=?",
            careerStartedOn, rank, wardId, subject, role,
        )
    }

    fun insertMembership(wardId: UUID, subject: String, role: String, canViewWardCalendar: Boolean) {
        jdbc.update(
            "INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,?,?)",
            wardId, subject, role, canViewWardCalendar,
        )
    }
}
