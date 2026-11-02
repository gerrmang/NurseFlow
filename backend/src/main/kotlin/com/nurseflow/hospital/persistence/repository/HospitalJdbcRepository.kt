package com.nurseflow.hospital.persistence.repository

import com.nurseflow.hospital.domain.HospitalRole
import com.nurseflow.hospital.persistence.entity.DbHospital
import com.nurseflow.hospital.persistence.entity.DbHospitalWard
import com.nurseflow.hospital.persistence.entity.NewWard
import com.nurseflow.hospital.persistence.entity.ReplayLookup
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * 병원·병원 역할·병원 소속 병동의 SQL 접근. HTTP 예외나 API DTO에 의존하지 않고, 조회 누락은 null/Boolean으로 돌려준다.
 */
@Repository
class HospitalJdbcRepository(private val jdbc: JdbcTemplate) {

    // ── Hospital ──

    fun findHospital(id: UUID): DbHospital? = jdbc.query(
        "SELECT * FROM hospitals WHERE hospital_id=?", { rs, _ -> mapHospital(rs) }, id,
    ).firstOrNull()

    fun findHospitalByName(name: String): DbHospital? = jdbc.query(
        "SELECT * FROM hospitals WHERE name=?", { rs, _ -> mapHospital(rs) }, name,
    ).firstOrNull()

    /** 병원 행을 잠근다. 같은 병원의 병동 생성을 직렬화한다. 병원이 없으면 null. */
    fun lockHospital(id: UUID): DbHospital? = jdbc.query(
        "SELECT * FROM hospitals WHERE hospital_id=? FOR UPDATE", { rs, _ -> mapHospital(rs) }, id,
    ).firstOrNull()

    fun insertHospital(id: UUID, name: String, zone: ZoneId, createdAt: Instant) {
        jdbc.update(
            "INSERT INTO hospitals(hospital_id,name,time_zone,created_at) VALUES(?,?,?,?)",
            id, name, zone.id, createdAt.toString(),
        )
    }

    // ── Hospital membership ──

    fun hospitalRoles(hospitalId: UUID, subject: String): Set<HospitalRole> = jdbc.query(
        "SELECT role FROM hospital_memberships WHERE hospital_id=? AND subject=?",
        { rs, _ -> HospitalRole.valueOf(rs.getString("role")) }, hospitalId, subject,
    ).toSet()

    fun insertHospitalMembership(hospitalId: UUID, subject: String, role: HospitalRole, grantedAt: Instant) {
        jdbc.update(
            "INSERT INTO hospital_memberships(hospital_id,subject,role,granted_at) VALUES(?,?,?,?)",
            hospitalId, subject, role.name, grantedAt.toString(),
        )
    }

    /** 병원 관리자 역할이 있는 병원 목록. */
    fun hospitalsAdministeredBy(subject: String): List<DbHospital> = jdbc.query(
        """SELECT h.* FROM hospitals h JOIN hospital_memberships m ON m.hospital_id=h.hospital_id
            WHERE m.subject=? AND m.role='HOSPITAL_ADMIN' ORDER BY h.name, h.hospital_id""",
        { rs, _ -> mapHospital(rs) }, subject,
    )

    /** 사용자가 이 병원 소속 병동 중 하나라도 병동 역할을 가지는지 확인한다. */
    fun hasWardMembershipInHospital(hospitalId: UUID, subject: String): Boolean =
        (jdbc.queryForObject(
            """SELECT COUNT(*) FROM ward_memberships m JOIN wards w ON w.ward_id=m.ward_id
                WHERE w.hospital_id=? AND m.subject=?""",
            Int::class.java, hospitalId, subject,
        ) ?: 0) > 0

    // ── Ward ──

    fun wardsOfHospital(hospitalId: UUID): List<DbHospitalWard> = jdbc.query(
        "SELECT * FROM wards WHERE hospital_id=? ORDER BY name, ward_id", { rs, _ -> mapWard(rs) }, hospitalId,
    )

    fun findWardInHospital(hospitalId: UUID, wardId: UUID): DbHospitalWard? = jdbc.query(
        "SELECT * FROM wards WHERE hospital_id=? AND ward_id=?", { rs, _ -> mapWard(rs) }, hospitalId, wardId,
    ).firstOrNull()

    fun wardNameExists(hospitalId: UUID, name: String): Boolean =
        (jdbc.queryForObject(
            "SELECT COUNT(*) FROM wards WHERE hospital_id=? AND name=?", Int::class.java, hospitalId, name,
        ) ?: 0) > 0

    fun insertWard(ward: NewWard) {
        jdbc.update(
            "INSERT INTO wards(ward_id,name,time_zone,hospital_id,created_at,created_by) VALUES(?,?,?,?,?,?)",
            ward.id, ward.name, ward.zone.id, ward.hospitalId, ward.createdAt.toString(), ward.createdBy,
        )
    }

    /** 기존 근무표 권한 검사가 읽는 `ward_memberships`에 병동 관리자 역할을 기록한다. */
    fun insertWardAdmin(wardId: UUID, subject: String) {
        jdbc.update(
            "INSERT INTO ward_memberships(ward_id,subject,role,can_view_ward_calendar) VALUES(?,?,'WARD_ADMIN',TRUE)",
            wardId, subject,
        )
    }

    // ── Idempotency replay (기존 mutation_replays 테이블 공유, operation 이름으로 기능을 구분) ──

    fun findReplay(key: UUID, operation: String, requestHash: String): ReplayLookup {
        val saved = jdbc.query(
            "SELECT operation,request_hash,response_json FROM mutation_replays WHERE idempotency_key=?",
            { rs, _ -> Triple(rs.getString("operation"), rs.getString("request_hash"), rs.getString("response_json")) },
            key,
        ).firstOrNull() ?: return ReplayLookup.Absent
        return if (saved.first == operation && saved.second == requestHash) {
            ReplayLookup.Match(saved.third)
        } else {
            ReplayLookup.Mismatch
        }
    }

    fun insertReplay(key: UUID, operation: String, requestHash: String, responseJson: String) {
        jdbc.update(
            "INSERT INTO mutation_replays(idempotency_key,operation,request_hash,response_json) VALUES(?,?,?,?)",
            key, operation, requestHash, responseJson,
        )
    }

    // ── Mapping ──

    private fun mapHospital(rs: ResultSet) = DbHospital(
        UUID.fromString(rs.getString("hospital_id")),
        rs.getString("name"),
        ZoneId.of(rs.getString("time_zone")),
        Instant.parse(rs.getString("created_at")),
    )

    private fun mapWard(rs: ResultSet) = DbHospitalWard(
        UUID.fromString(rs.getString("ward_id")),
        UUID.fromString(rs.getString("hospital_id")),
        rs.getString("name"),
        ZoneId.of(rs.getString("time_zone")),
        rs.getString("created_at")?.let(Instant::parse),
    )
}
