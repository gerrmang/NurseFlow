package com.nurseflow.ward.persistence.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

data class DbMembershipRow(
    val wardId: UUID,
    val wardName: String,
    val role: String,
    val canViewWardCalendar: Boolean,
    val hospitalName: String?,
)

data class DbWardLabel(val name: String, val hospitalName: String?)

/** Hospital chosen at creation, copied from the official list at that time. */
data class NewWard(
    val wardId: UUID,
    val name: String,
    val timeZone: String,
    val hospitalSource: String,
    val hospitalExternalId: String,
    val hospitalName: String,
    val hospitalAddress: String,
    val createdBy: String,
    val createdAt: Instant,
)

@Repository
class WardJdbcRepository(private val jdbc: JdbcTemplate) {
    fun membershipsOf(subject: String): List<DbMembershipRow> = jdbc.query(
        """SELECT m.ward_id, w.name, m.role, m.can_view_ward_calendar, w.hospital_name
           FROM ward_memberships m JOIN wards w ON w.ward_id = m.ward_id
           WHERE m.subject=? ORDER BY w.name, w.hospital_name, m.ward_id, m.role""",
        { rs, _ ->
            DbMembershipRow(
                rs.getObject("ward_id", UUID::class.java), rs.getString("name"),
                rs.getString("role"), rs.getBoolean("can_view_ward_calendar"), rs.getString("hospital_name"),
            )
        },
        subject,
    )

    fun rolesIn(wardId: UUID, subject: String): Set<String> = jdbc.query(
        "SELECT role FROM ward_memberships WHERE ward_id=? AND subject=?",
        { rs, _ -> rs.getString("role") }, wardId, subject,
    ).toSet()

    fun findWardName(wardId: UUID): String? = findWardLabel(wardId)?.name

    fun findWardLabel(wardId: UUID): DbWardLabel? = jdbc.query(
        "SELECT name, hospital_name FROM wards WHERE ward_id=?",
        { rs, _ -> DbWardLabel(rs.getString("name"), rs.getString("hospital_name")) }, wardId,
    ).firstOrNull()

    /** Serializes membership changes in one ward (same lock as roster mutations). */
    fun lockWard(wardId: UUID): Boolean =
        jdbc.query("SELECT ward_id FROM wards WHERE ward_id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, wardId)
            .isNotEmpty()

    fun insertWard(ward: NewWard) {
        jdbc.update(
            """INSERT INTO wards(ward_id,name,time_zone,hospital_source,hospital_external_id,hospital_name,
                                 hospital_address,created_by,created_at)
               VALUES(?,?,?,?,?,?,?,?,?)""",
            ward.wardId, ward.name, ward.timeZone, ward.hospitalSource, ward.hospitalExternalId,
            ward.hospitalName, ward.hospitalAddress, ward.createdBy, ward.createdAt.toString(),
        )
    }

    fun insertMembership(wardId: UUID, subject: String, role: String, canViewWardCalendar: Boolean) {
        jdbc.update(
            "INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,?,?)",
            wardId, subject, role, canViewWardCalendar,
        )
    }

    /** Saved response for an Idempotency-Key, with the operation and request hash it was stored for. */
    fun findReplay(key: UUID): Triple<String, String, String>? = jdbc.query(
        "SELECT operation,request_hash,response_json FROM mutation_replays WHERE idempotency_key=?",
        { rs, _ -> Triple(rs.getString("operation"), rs.getString("request_hash"), rs.getString("response_json")) },
        key,
    ).firstOrNull()

    fun insertReplay(key: UUID, operation: String, requestHash: String, responseJson: String) {
        jdbc.update(
            "INSERT INTO mutation_replays(idempotency_key,operation,request_hash,response_json) VALUES(?,?,?,?)",
            key, operation, requestHash, responseJson,
        )
    }
}
