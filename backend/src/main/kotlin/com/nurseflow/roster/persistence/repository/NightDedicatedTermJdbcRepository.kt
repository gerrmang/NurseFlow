package com.nurseflow.roster.persistence.repository

import com.nurseflow.roster.domain.model.NightDedicatedTerm
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

@Repository
class NightDedicatedTermJdbcRepository(private val jdbc: JdbcTemplate) {
    fun insert(term: NightDedicatedTerm) {
        jdbc.update(
            """INSERT INTO night_dedicated_terms(term_id,ward_id,nurse_id,start_month,planned_end_month,end_month,
                created_at,created_by) VALUES(?,?,?,?,?,?,?,?)""",
            term.id, term.wardId, term.nurseId, term.startMonth.toString(), term.plannedEndMonth.toString(),
            term.endMonth.toString(), term.createdAt.toString(), term.createdBy,
        )
    }

    /** Every term of the ward, cancelled ones included, ordered by start month then nurse. */
    fun list(wardId: UUID): List<NightDedicatedTerm> = jdbc.query(
        "SELECT * FROM night_dedicated_terms WHERE ward_id=?", { rs, _ -> map(rs) }, wardId,
    ).sortedWith(compareBy({ it.startMonth }, { it.nurseId.toString() }, { it.id.toString() }))

    fun forNurse(wardId: UUID, nurseId: UUID): List<NightDedicatedTerm> = jdbc.query(
        "SELECT * FROM night_dedicated_terms WHERE ward_id=? AND nurse_id=?", { rs, _ -> map(rs) }, wardId, nurseId,
    )

    fun find(wardId: UUID, id: UUID): NightDedicatedTerm? = jdbc.query(
        "SELECT * FROM night_dedicated_terms WHERE ward_id=? AND term_id=?", { rs, _ -> map(rs) }, wardId, id,
    ).firstOrNull()

    /** Caller holds the ward lock. */
    fun end(id: UUID, endMonth: YearMonth, at: Instant, by: String) {
        jdbc.update(
            "UPDATE night_dedicated_terms SET end_month=?,ended_at=?,ended_by=? WHERE term_id=?",
            endMonth.toString(), at.toString(), by, id,
        )
    }

    private fun map(rs: ResultSet) = NightDedicatedTerm(
        id = UUID.fromString(rs.getString("term_id")),
        wardId = UUID.fromString(rs.getString("ward_id")),
        nurseId = UUID.fromString(rs.getString("nurse_id")),
        startMonth = YearMonth.parse(rs.getString("start_month")),
        plannedEndMonth = YearMonth.parse(rs.getString("planned_end_month")),
        endMonth = YearMonth.parse(rs.getString("end_month")),
        createdAt = Instant.parse(rs.getString("created_at")),
        createdBy = rs.getString("created_by"),
        endedAt = rs.getString("ended_at")?.let(Instant::parse),
        endedBy = rs.getString("ended_by"),
    )
}
