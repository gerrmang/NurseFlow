package com.nurseflow.roster.persistence.repository

import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.domain.readiness.ReadinessTeam
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.time.YearMonth
import java.util.UUID

/** Read-only lookups of ward settings for the setup readiness check (A18). */
@Repository
class SetupReadinessJdbcRepository(private val jdbc: JdbcTemplate) {
    data class WardRules(val maxConsecutiveWorkDays: Int, val maxOffRequestsPerMonth: Int, val offRequestDeadlineDay: Int)

    fun activeShiftKinds(wardId: UUID): Set<ShiftKind> = jdbc.query(
        "SELECT DISTINCT kind FROM shift_types WHERE ward_id=? AND active=TRUE",
        { rs, _ -> ShiftKind.valueOf(rs.getString("kind")) }, wardId,
    ).toSet()

    fun activeTeams(wardId: UUID): List<ReadinessTeam> = jdbc.query(
        "SELECT team_id,name FROM ward_teams WHERE ward_id=? AND active=TRUE ORDER BY sort_order,team_id",
        { rs, _ -> ReadinessTeam(rs.getObject("team_id", UUID::class.java), rs.getString("name")) }, wardId,
    )

    fun wardRules(wardId: UUID): WardRules? = jdbc.query(
        "SELECT max_consecutive_work_days,max_off_requests_per_month,off_request_deadline_day FROM wards WHERE ward_id=?",
        { rs, _ -> WardRules(rs.getInt(1), rs.getInt(2), rs.getInt(3)) }, wardId,
    ).firstOrNull()

    /** Submitted, not yet decided off requests of the month: generation only treats them as preferences. */
    fun pendingOffRequests(wardId: UUID, month: YearMonth): Int = jdbc.queryForObject(
        "SELECT COUNT(*) FROM off_requests WHERE ward_id=? AND status='SUBMITTED' AND off_date BETWEEN ? AND ?",
        Int::class.java, wardId, Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()),
    ) ?: 0
}
