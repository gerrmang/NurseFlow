package com.nurseflow.roster.persistence.repository

import com.nurseflow.roster.application.model.AcknowledgementKind
import com.nurseflow.roster.application.model.ConfirmationReasons
import com.nurseflow.roster.application.model.HourShortfallDiagnostic
import com.nurseflow.roster.application.model.UnfilledSlotDiagnostic
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class StoredConfirmation(
    val rosterId: UUID,
    val revision: Int,
    val confirmedAt: Instant,
    val generalUnfilled: List<UnfilledSlotDiagnostic>,
    val seniorUnfilled: List<UnfilledSlotDiagnostic>,
    val hourShortfalls: List<HourShortfallDiagnostic>,
)

/** A16 acknowledgement snapshots, written once in the confirm transaction and never changed. */
@Repository
class ConfirmationJdbcRepository(private val jdbc: JdbcTemplate) {
    fun insert(
        rosterId: UUID, revision: Int, confirmedBy: String, confirmedAt: Instant,
        generalUnfilled: List<UnfilledSlotDiagnostic>,
        seniorUnfilled: List<UnfilledSlotDiagnostic>,
        hourShortfalls: List<HourShortfallDiagnostic>,
    ) {
        jdbc.update(
            "INSERT INTO roster_confirmations(roster_id,revision,confirmed_by,confirmed_at) VALUES(?,?,?,?)",
            rosterId, revision, confirmedBy, confirmedAt.toString(),
        )
        var no = 0
        fun slot(kind: AcknowledgementKind, it: UnfilledSlotDiagnostic) = jdbc.update(
            """INSERT INTO roster_confirmation_items(roster_id,item_no,kind,slot_id,shift_date,shift_type_id,shift_code,team_id,reason)
               VALUES(?,?,?,?,?,?,?,?,?)""",
            rosterId, ++no, kind.name, it.slotId, it.date, it.shiftTypeId, it.shiftCode, it.teamId, it.reason,
        )
        generalUnfilled.forEach { slot(AcknowledgementKind.GENERAL_UNFILLED, it) }
        seniorUnfilled.forEach { slot(AcknowledgementKind.SENIOR_UNFILLED, it) }
        hourShortfalls.forEach {
            jdbc.update(
                """INSERT INTO roster_confirmation_items(roster_id,item_no,kind,nurse_id,display_name,target_minutes,credit_minutes,shortfall_minutes,reason)
                   VALUES(?,?,?,?,?,?,?,?,?)""",
                rosterId, ++no, AcknowledgementKind.HOUR_SHORTFALL.name, it.nurseId, it.displayName,
                it.targetMinutes, it.creditMinutes, it.shortfallMinutes, it.reason,
            )
        }
    }

    fun find(rosterId: UUID): StoredConfirmation? {
        val header = jdbc.query(
            "SELECT revision,confirmed_at FROM roster_confirmations WHERE roster_id=?",
            { rs, _ -> rs.getInt("revision") to Instant.parse(rs.getString("confirmed_at")) }, rosterId,
        ).singleOrNull() ?: return null
        val rows = jdbc.query(
            "SELECT * FROM roster_confirmation_items WHERE roster_id=? ORDER BY item_no",
            { rs, _ -> row(rs) }, rosterId,
        )
        return StoredConfirmation(
            rosterId, header.first, header.second,
            rows.filter { it.first == AcknowledgementKind.GENERAL_UNFILLED }.map { it.second as UnfilledSlotDiagnostic },
            rows.filter { it.first == AcknowledgementKind.SENIOR_UNFILLED }.map { it.second as UnfilledSlotDiagnostic },
            rows.filter { it.first == AcknowledgementKind.HOUR_SHORTFALL }.map { it.second as HourShortfallDiagnostic },
        )
    }

    private fun row(rs: ResultSet): Pair<AcknowledgementKind, Any> {
        val kind = AcknowledgementKind.valueOf(rs.getString("kind"))
        val reason = rs.getString("reason")
        return kind to when (kind) {
            AcknowledgementKind.HOUR_SHORTFALL -> HourShortfallDiagnostic(
                UUID.fromString(rs.getString("nurse_id")), rs.getString("display_name"),
                rs.getInt("target_minutes"), rs.getInt("credit_minutes"), rs.getInt("shortfall_minutes"),
                reason, ConfirmationReasons.message(reason),
            )
            else -> UnfilledSlotDiagnostic(
                UUID.fromString(rs.getString("slot_id")), rs.getDate("shift_date").toLocalDate(),
                UUID.fromString(rs.getString("shift_type_id")), rs.getString("shift_code"),
                rs.getString("team_id")?.let(UUID::fromString), reason, ConfirmationReasons.message(reason),
            )
        }
    }
}
