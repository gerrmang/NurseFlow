package com.nurseflow.offrequest.persistence.repository

import com.nurseflow.offrequest.domain.OffRequest
import com.nurseflow.offrequest.domain.OffRequestPolicy
import com.nurseflow.offrequest.domain.OffRequestSource
import com.nurseflow.offrequest.domain.OffRequestStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

@Repository
class OffRequestJdbcRepository(private val jdbc: JdbcTemplate) {
    fun insert(request: OffRequest) {
        jdbc.update(
            """INSERT INTO off_requests(request_id,ward_id,nurse_id,off_date,status,source,submitted_by,submitted_at,
                after_deadline) VALUES(?,?,?,?,?,?,?,?,?)""",
            request.id, request.wardId, request.nurseId, Date.valueOf(request.date), request.status.name,
            request.source.name, request.submittedBy, request.submittedAt.toString(), request.afterDeadline,
        )
    }

    fun find(wardId: UUID, requestId: UUID): OffRequest? = jdbc.query(
        "$SELECT WHERE ward_id=? AND request_id=?", { rs, _ -> map(rs) }, wardId, requestId,
    ).firstOrNull()

    fun list(wardId: UUID, month: YearMonth, nurseId: UUID? = null): List<OffRequest> {
        val args = mutableListOf<Any>(wardId, Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()))
        val nurseFilter = nurseId?.let { args += it; " AND nurse_id=?" }.orEmpty()
        return jdbc.query(
            "$SELECT WHERE ward_id=? AND off_date BETWEEN ? AND ?$nurseFilter ORDER BY off_date,nurse_id,submitted_at",
            { rs, _ -> map(rs) }, *args.toTypedArray(),
        )
    }

    fun holdsDate(nurseId: UUID, date: LocalDate): Boolean = (jdbc.queryForObject(
        "SELECT COUNT(*) FROM off_requests WHERE nurse_id=? AND off_date=? AND status IN ('SUBMITTED','CONFIRMED')",
        Int::class.java, nurseId, Date.valueOf(date),
    ) ?: 0) > 0

    /** Open (SUBMITTED or CONFIRMED) requests the nurse entered themselves in [month]; admin entries do not count. */
    fun openNurseEntries(nurseId: UUID, month: YearMonth): Int = jdbc.queryForObject(
        """SELECT COUNT(*) FROM off_requests WHERE nurse_id=? AND source='NURSE'
            AND status IN ('SUBMITTED','CONFIRMED') AND off_date BETWEEN ? AND ?""",
        Int::class.java, nurseId, Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()),
    ) ?: 0

    fun policy(wardId: UUID): OffRequestPolicy? = jdbc.query(
        "SELECT max_off_requests_per_month,off_request_deadline_day FROM wards WHERE ward_id=?",
        { rs, _ -> OffRequestPolicy(rs.getInt(1), rs.getInt(2)) }, wardId,
    ).firstOrNull()

    fun setPolicy(wardId: UUID, policy: OffRequestPolicy) {
        jdbc.update(
            "UPDATE wards SET max_off_requests_per_month=?,off_request_deadline_day=? WHERE ward_id=?",
            policy.monthlyLimit, policy.deadlineDay, wardId,
        )
    }

    fun wardZone(wardId: UUID): ZoneId? = jdbc.query(
        "SELECT time_zone FROM wards WHERE ward_id=?", { rs, _ -> ZoneId.of(rs.getString(1)) }, wardId,
    ).firstOrNull()

    fun decide(requestId: UUID, status: OffRequestStatus, decidedBy: String, decidedAt: Instant) {
        jdbc.update(
            "UPDATE off_requests SET status=?,decided_by=?,decided_at=? WHERE request_id=?",
            status.name, decidedBy, decidedAt.toString(), requestId,
        )
    }

    /** A confirmed day off changes what is valid in the month's drafts; published rosters stay until re-confirmed. */
    fun invalidateMonthDrafts(wardId: UUID, month: YearMonth) {
        jdbc.update(
            "UPDATE rosters SET revision=revision+1,needs_revalidation=TRUE WHERE ward_id=? AND roster_month=? AND status='DRAFT'",
            wardId, month.toString(),
        )
    }

    private fun map(rs: ResultSet) = OffRequest(
        id = rs.getObject("request_id", UUID::class.java),
        wardId = rs.getObject("ward_id", UUID::class.java),
        nurseId = rs.getObject("nurse_id", UUID::class.java),
        date = rs.getDate("off_date").toLocalDate(),
        status = OffRequestStatus.valueOf(rs.getString("status")),
        source = OffRequestSource.valueOf(rs.getString("source")),
        submittedBy = rs.getString("submitted_by"),
        submittedAt = Instant.parse(rs.getString("submitted_at")),
        decidedBy = rs.getString("decided_by"),
        decidedAt = rs.getString("decided_at")?.let(Instant::parse),
        afterDeadline = rs.getBoolean("after_deadline"),
    )

    private companion object {
        const val SELECT = """SELECT request_id,ward_id,nurse_id,off_date,status,source,submitted_by,submitted_at,
            decided_by,decided_at,after_deadline FROM off_requests"""
    }
}
