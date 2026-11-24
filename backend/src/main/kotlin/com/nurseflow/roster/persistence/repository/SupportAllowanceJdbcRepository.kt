package com.nurseflow.roster.persistence.repository

import com.nurseflow.roster.domain.model.SupportAllowance
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.sql.Date
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Repository
class SupportAllowanceJdbcRepository(
    private val jdbc: JdbcTemplate,
    private val mapper: JsonMapper,
) {
    private val strings = object : TypeReference<List<String>>() {}

    fun insert(allowance: SupportAllowance) {
        jdbc.update(
            """INSERT INTO cross_team_support_allowances
                (allowance_id,ward_id,nurse_id,team_id,start_date,end_date,shift_type_ids,created_at,created_by)
                VALUES(?,?,?,?,?,?,?,?,?)""",
            allowance.id, allowance.wardId, allowance.nurseId, allowance.teamId,
            Date.valueOf(allowance.startDate), Date.valueOf(allowance.endDate),
            mapper.writeValueAsString(allowance.shiftTypeIds.map(UUID::toString).sorted()),
            allowance.createdAt.toString(), allowance.createdBy,
        )
    }

    fun list(wardId: UUID, includeRevoked: Boolean): List<SupportAllowance> = jdbc.query(
        """SELECT * FROM cross_team_support_allowances WHERE ward_id=?
            ${if (includeRevoked) "" else "AND revoked_at IS NULL"}
            ORDER BY start_date,end_date,created_at,allowance_id""",
        { rs, _ -> map(rs) }, wardId,
    )

    fun find(wardId: UUID, id: UUID): SupportAllowance? = jdbc.query(
        "SELECT * FROM cross_team_support_allowances WHERE ward_id=? AND allowance_id=?",
        { rs, _ -> map(rs) }, wardId, id,
    ).firstOrNull()

    /** Unrevoked allowances of the ward whose dates touch [from]..[to]. */
    fun activeOverlapping(wardId: UUID, from: LocalDate, to: LocalDate): List<SupportAllowance> = jdbc.query(
        """SELECT * FROM cross_team_support_allowances
            WHERE ward_id=? AND revoked_at IS NULL AND start_date<=? AND end_date>=?""",
        { rs, _ -> map(rs) }, wardId, Date.valueOf(to), Date.valueOf(from),
    )

    fun revoke(id: UUID, at: Instant, by: String): Boolean = jdbc.update(
        "UPDATE cross_team_support_allowances SET revoked_at=?,revoked_by=? WHERE allowance_id=? AND revoked_at IS NULL",
        at.toString(), by, id,
    ) == 1

    /**
     * Flags the ward's DRAFT rosters holding an assignment of the allowance's nurse on its team within its dates,
     * the drafts a revocation can invalidate. Another allowance may still cover a flagged slot; confirmation
     * re-checks every assignment either way. Returns the number of drafts flagged.
     */
    fun markAffectedDrafts(allowance: SupportAllowance): Int = jdbc.update(
        """UPDATE rosters SET revision=revision+1,needs_revalidation=TRUE
            WHERE ward_id=? AND status='DRAFT' AND roster_id IN (
                SELECT s.roster_id FROM roster_slots s
                WHERE s.nurse_id=? AND s.team_id=? AND s.shift_date BETWEEN ? AND ?)""",
        allowance.wardId, allowance.nurseId, allowance.teamId,
        Date.valueOf(allowance.startDate), Date.valueOf(allowance.endDate),
    )

    private fun map(rs: ResultSet) = SupportAllowance(
        id = uuid(rs, "allowance_id"),
        wardId = uuid(rs, "ward_id"),
        nurseId = uuid(rs, "nurse_id"),
        teamId = uuid(rs, "team_id"),
        startDate = rs.getDate("start_date").toLocalDate(),
        endDate = rs.getDate("end_date").toLocalDate(),
        shiftTypeIds = mapper.readValue(rs.getString("shift_type_ids"), strings).map(UUID::fromString).toSet(),
        createdAt = Instant.parse(rs.getString("created_at")),
        createdBy = rs.getString("created_by"),
        revokedAt = rs.getString("revoked_at")?.let(Instant::parse),
        revokedBy = rs.getString("revoked_by"),
    )

    private fun uuid(rs: ResultSet, column: String): UUID = UUID.fromString(rs.getString(column))
}
