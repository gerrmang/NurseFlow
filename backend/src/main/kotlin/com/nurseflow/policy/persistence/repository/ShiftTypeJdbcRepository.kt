package com.nurseflow.policy.persistence.repository

import com.nurseflow.policy.application.model.ShiftTypeRow
import com.nurseflow.roster.domain.model.ShiftKind
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

@Repository
class ShiftTypeJdbcRepository(private val jdbc: JdbcTemplate) {
    /** Serializes every policy write of a ward; also the guard against duplicate codes and lost revision bumps. */
    fun lockWard(wardId: UUID): Boolean =
        jdbc.query("SELECT ward_id FROM wards WHERE ward_id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, wardId).isNotEmpty()

    fun list(wardId: UUID, includeInactive: Boolean): List<ShiftTypeRow> = jdbc.query(
        "SELECT * FROM shift_types WHERE ward_id=?" + (if (includeInactive) "" else " AND active=TRUE") +
            " ORDER BY start_time, code",
        { rs, _ -> map(rs) }, wardId,
    )

    fun find(wardId: UUID, shiftTypeId: UUID): ShiftTypeRow? = jdbc.query(
        "SELECT * FROM shift_types WHERE ward_id=? AND shift_type_id=?", { rs, _ -> map(rs) }, wardId, shiftTypeId,
    ).firstOrNull()

    fun codeExists(wardId: UUID, code: String, except: UUID?): Boolean = (jdbc.queryForObject(
        "SELECT COUNT(*) FROM shift_types WHERE ward_id=? AND code=? AND (? IS NULL OR shift_type_id<>?)",
        Int::class.java, wardId, code, except, except,
    ) ?: 0) > 0

    fun insert(row: ShiftTypeRow) {
        jdbc.update(
            """INSERT INTO shift_types(shift_type_id,ward_id,code,display_name,kind,start_time,end_time,spans_midnight,
                   active,break_minutes,recognized_minutes,target_credit_minutes) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""",
            row.id, row.wardId, row.code, row.displayName, row.kind.name, row.start, row.end, row.spansMidnight,
            row.active, row.breakMinutes, row.recognizedMinutes, row.targetCreditMinutes,
        )
    }

    fun update(row: ShiftTypeRow) {
        jdbc.update(
            """UPDATE shift_types SET code=?,display_name=?,kind=?,start_time=?,end_time=?,spans_midnight=?,active=?,
                   break_minutes=?,recognized_minutes=?,target_credit_minutes=? WHERE shift_type_id=? AND ward_id=?""",
            row.code, row.displayName, row.kind.name, row.start, row.end, row.spansMidnight, row.active,
            row.breakMinutes, row.recognizedMinutes, row.targetCreditMinutes, row.id, row.wardId,
        )
    }

    fun policyRevision(wardId: UUID): Int = jdbc.query(
        "SELECT revision FROM ward_policy_revisions WHERE ward_id=?", { rs, _ -> rs.getInt(1) }, wardId,
    ).firstOrNull() ?: 0

    /** Caller holds the ward lock. */
    fun bumpPolicyRevision(wardId: UUID, actor: String, now: Instant) {
        val updated = jdbc.update(
            "UPDATE ward_policy_revisions SET revision=revision+1,updated_at=?,updated_by=? WHERE ward_id=?",
            now.toString(), actor, wardId,
        )
        if (updated == 0) {
            jdbc.update(
                "INSERT INTO ward_policy_revisions(ward_id,revision,updated_at,updated_by) VALUES(?,1,?,?)",
                wardId, now.toString(), actor,
            )
        }
    }

    /** Marks DRAFT rosters of the ward that already have slots of this shift type. Published rosters are untouched. */
    fun markDraftsForRevalidation(wardId: UUID, shiftTypeId: UUID): Int = jdbc.update(
        """UPDATE rosters SET needs_revalidation=TRUE
            WHERE ward_id=? AND status='DRAFT' AND needs_revalidation=FALSE
              AND roster_id IN (SELECT roster_id FROM roster_slots WHERE shift_type_id=?)""",
        wardId, shiftTypeId,
    )

    private fun map(rs: ResultSet) = ShiftTypeRow(
        id = UUID.fromString(rs.getString("shift_type_id")),
        wardId = UUID.fromString(rs.getString("ward_id")),
        code = rs.getString("code"),
        displayName = rs.getString("display_name"),
        kind = ShiftKind.valueOf(rs.getString("kind")),
        start = rs.getObject("start_time", LocalTime::class.java),
        end = rs.getObject("end_time", LocalTime::class.java),
        spansMidnight = rs.getBoolean("spans_midnight"),
        active = rs.getBoolean("active"),
        breakMinutes = rs.getInt("break_minutes"),
        recognizedMinutes = rs.getObject("recognized_minutes") as Int?,
        targetCreditMinutes = rs.getObject("target_credit_minutes") as Int?,
    )
}
