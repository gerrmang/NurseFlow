package com.nurseflow.ward.persistence.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

data class DbWardMembershipRow(
    val wardId: UUID,
    val wardName: String,
    val timeZone: String,
    val hospitalId: UUID?,
    val hospitalName: String?,
    val role: String,
    val canViewWardCalendar: Boolean,
)

/** 현재 사용자 본인의 병동 소속 행만 읽는다. 다른 사용자·다른 병동은 조회하지 않는다. */
@Repository
class WardMembershipJdbcRepository(private val jdbc: JdbcTemplate) {
    fun membershipsOf(subject: String): List<DbWardMembershipRow> = jdbc.query(
        """SELECT m.ward_id, w.name AS ward_name, w.time_zone, w.hospital_id, h.name AS hospital_name,
                  m.role, m.can_view_ward_calendar
             FROM ward_memberships m
             JOIN wards w ON w.ward_id = m.ward_id
             LEFT JOIN hospitals h ON h.hospital_id = w.hospital_id
            WHERE m.subject = ?
            ORDER BY h.name, w.name, w.ward_id, m.role""",
        { rs, _ ->
            DbWardMembershipRow(
                UUID.fromString(rs.getString("ward_id")),
                rs.getString("ward_name"),
                rs.getString("time_zone"),
                rs.getString("hospital_id")?.let(UUID::fromString),
                rs.getString("hospital_name"),
                rs.getString("role"),
                rs.getBoolean("can_view_ward_calendar"),
            )
        },
        subject,
    )
}
