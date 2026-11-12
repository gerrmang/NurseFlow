package com.nurseflow.policy.persistence.repository

import com.nurseflow.policy.application.model.ShiftTypeRow
import com.nurseflow.roster.domain.model.ShiftKind
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalTime
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Repository
class ShiftTypeJdbcRepository(private val jdbc: JdbcTemplate) {
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

    /**
     * Rewrites the start/end instants of the type's slots in DRAFT rosters from the new hours (slots store the
     * times they were created with, and confirmation checks rules against them). Every slot is computed first:
     * if any would end before it starts in the ward's time zone (a daylight-saving gap or overlap moves one end),
     * nothing is written and false is returned. The revision is bumped by [markDraftsForRevalidation], which every
     * such change also calls.
     */
    fun recomputeDraftSlotTimes(wardId: UUID, shiftTypeId: UUID, start: LocalTime, end: LocalTime, spansMidnight: Boolean): Boolean {
        val slots = jdbc.query(
            """SELECT s.slot_id, s.roster_id, s.shift_date, w.time_zone FROM roster_slots s
               JOIN rosters r ON r.roster_id=s.roster_id JOIN wards w ON w.ward_id=r.ward_id
               WHERE r.ward_id=? AND r.status='DRAFT' AND s.shift_type_id=?""",
            { rs, _ ->
                SlotDate(
                    UUID.fromString(rs.getString("slot_id")), UUID.fromString(rs.getString("roster_id")),
                    rs.getObject("shift_date", LocalDate::class.java), ZoneId.of(rs.getString("time_zone")),
                )
            },
            wardId, shiftTypeId,
        )
        val times = slots.map { slot ->
            val startsAt = slot.date.atTime(start).atZone(slot.zone).toInstant()
            val endsAt = (if (spansMidnight) slot.date.plusDays(1) else slot.date).atTime(end).atZone(slot.zone).toInstant()
            Triple(slot.id, startsAt, endsAt)
        }
        if (times.any { (_, startsAt, endsAt) -> !startsAt.isBefore(endsAt) }) return false
        times.forEach { (id, startsAt, endsAt) ->
            jdbc.update("UPDATE roster_slots SET starts_at=?, ends_at=? WHERE slot_id=?", startsAt.toString(), endsAt.toString(), id)
        }
        return true
    }

    /** DRAFT slots follow the type's code and kind; published slots keep the values they were published with. */
    fun syncDraftSlotCodeAndKind(wardId: UUID, shiftTypeId: UUID, code: String, kind: ShiftKind) {
        jdbc.update(
            """UPDATE roster_slots SET shift_code=?, shift_kind=?
                WHERE shift_type_id=? AND roster_id IN (SELECT roster_id FROM rosters WHERE ward_id=? AND status='DRAFT')""",
            code, kind.name, shiftTypeId, wardId,
        )
    }

    private data class SlotDate(val id: UUID, val rosterId: UUID, val date: LocalDate, val zone: ZoneId)

    /**
     * Flags DRAFT rosters of the ward that have slots of this shift type and bumps their revision, so a client
     * holding the view from before the change gets 412 on confirm and must reload under the current policy.
     * Published rosters are untouched.
     */
    fun markDraftsForRevalidation(wardId: UUID, shiftTypeId: UUID): Int = jdbc.update(
        """UPDATE rosters SET needs_revalidation=TRUE, revision=revision+1
            WHERE ward_id=? AND status='DRAFT'
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
