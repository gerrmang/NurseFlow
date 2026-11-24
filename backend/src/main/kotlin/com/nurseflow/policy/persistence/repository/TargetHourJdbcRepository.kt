package com.nurseflow.policy.persistence.repository

import com.nurseflow.policy.application.service.ShiftTypeService
import com.nurseflow.policy.domain.CalendarDay
import com.nurseflow.policy.domain.CalendarDayKind
import com.nurseflow.policy.domain.HourAbsence
import com.nurseflow.policy.domain.HourRestDay
import com.nurseflow.policy.domain.HourShift
import com.nurseflow.policy.domain.TargetHourPolicy
import com.nurseflow.roster.domain.model.ApprovedAbsenceKind
import com.nurseflow.roster.domain.model.RestDayKind
import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.domain.model.WorkPattern
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.Date
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

data class WardNurseRow(val nurseId: UUID, val displayName: String, val workPattern: WorkPattern)

@Repository
class TargetHourJdbcRepository(private val jdbc: JdbcTemplate) {
    fun policy(wardId: UUID): TargetHourPolicy? = jdbc.query(
        "SELECT * FROM ward_target_hour_policies WHERE ward_id=?",
        { rs, _ ->
            TargetHourPolicy(
                rs.getInt("daily_standard_minutes"), rs.getInt("leave_credit_minutes"),
                rs.getInt("half_day_credit_minutes"), rs.getInt("sof_credit_minutes"),
                rs.getObject("night_dedicated_target_minutes") as Int?,
                rs.getObject("night_dedicated_night_credit_minutes") as Int?,
            )
        }, wardId,
    ).firstOrNull()

    /** Caller holds the ward lock. */
    fun savePolicy(wardId: UUID, policy: TargetHourPolicy, actor: String, now: Instant) {
        jdbc.update("DELETE FROM ward_target_hour_policies WHERE ward_id=?", wardId)
        jdbc.update(
            """INSERT INTO ward_target_hour_policies(ward_id,daily_standard_minutes,leave_credit_minutes,half_day_credit_minutes,
                sof_credit_minutes,night_dedicated_target_minutes,night_dedicated_night_credit_minutes,updated_by,updated_at)
                VALUES(?,?,?,?,?,?,?,?,?)""",
            wardId, policy.dailyStandardMinutes, policy.leaveCreditMinutes, policy.halfDayCreditMinutes,
            policy.sofCreditMinutes, policy.nightDedicatedTargetMinutes, policy.nightDedicatedNightCreditMinutes,
            actor, now.toString(),
        )
    }

    fun calendar(wardId: UUID, month: YearMonth): List<CalendarDay> = jdbc.query(
        "SELECT calendar_date,kind,name FROM ward_calendar_days WHERE ward_id=? AND calendar_date BETWEEN ? AND ? ORDER BY calendar_date",
        { rs, _ -> CalendarDay(rs.getDate("calendar_date").toLocalDate(), CalendarDayKind.valueOf(rs.getString("kind")), rs.getString("name")) },
        wardId, Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()),
    )

    /** Caller holds the ward lock. Replaces every entry of [month]. */
    fun replaceCalendar(wardId: UUID, month: YearMonth, days: List<CalendarDay>, actor: String, now: Instant) {
        jdbc.update(
            "DELETE FROM ward_calendar_days WHERE ward_id=? AND calendar_date BETWEEN ? AND ?",
            wardId, Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()),
        )
        jdbc.batchUpdate(
            "INSERT INTO ward_calendar_days(ward_id,calendar_date,kind,name,updated_by,updated_at) VALUES(?,?,?,?,?,?)",
            days.map { arrayOf<Any?>(wardId, Date.valueOf(it.date), it.kind.name, it.name, actor, now.toString()) },
        )
    }

    fun publicHolidays(month: YearMonth): List<CalendarDay> = jdbc.query(
        "SELECT holiday_date,kind,name FROM public_holidays WHERE holiday_date BETWEEN ? AND ? ORDER BY holiday_date",
        { rs, _ -> CalendarDay(rs.getDate("holiday_date").toLocalDate(), CalendarDayKind.valueOf(rs.getString("kind")), rs.getString("name")) },
        Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()),
    )

    /** When [month] was last fetched from the holiday source, or null if never. */
    fun publicHolidayFetchedAt(month: YearMonth): Instant? = jdbc.query(
        "SELECT fetched_at FROM public_holiday_imports WHERE holiday_month=?",
        { rs, _ -> Instant.parse(rs.getString("fetched_at")) }, month.toString(),
    ).firstOrNull()

    /** Replaces the stored holidays of [month] in one transaction of the caller. */
    fun replacePublicHolidays(month: YearMonth, days: List<CalendarDay>, now: Instant) {
        jdbc.update(
            "DELETE FROM public_holidays WHERE holiday_date BETWEEN ? AND ?",
            Date.valueOf(month.atDay(1)), Date.valueOf(month.atEndOfMonth()),
        )
        jdbc.batchUpdate(
            "INSERT INTO public_holidays(holiday_date,kind,name) VALUES(?,?,?)",
            days.map { arrayOf<Any?>(Date.valueOf(it.date), it.kind.name, it.name) },
        )
        jdbc.update("DELETE FROM public_holiday_imports WHERE holiday_month=?", month.toString())
        jdbc.update(
            "INSERT INTO public_holiday_imports(holiday_month,fetched_at,holiday_count) VALUES(?,?,?)",
            month.toString(), now.toString(), days.size,
        )
    }

    fun wardNurses(wardId: UUID): List<WardNurseRow> = jdbc.query(
        "SELECT nurse_id,display_name,work_pattern FROM nurses WHERE ward_id=? ORDER BY display_name,nurse_id",
        { rs, _ ->
            WardNurseRow(
                UUID.fromString(rs.getString("nurse_id")), rs.getString("display_name"),
                WorkPattern.valueOf(rs.getString("work_pattern")),
            )
        }, wardId,
    )

    /**
     * Assigned slots of a roster with the minutes of their shift type. Slots store times, code and kind but not
     * minutes, so a published roster is credited with its types' current minutes.
     */
    fun assignedShifts(rosterId: UUID): List<HourShift> = jdbc.query(
        """SELECT s.nurse_id,s.shift_date,s.starts_at,s.ends_at,COALESCE(s.shift_kind,t.kind) AS kind,t.start_time,t.end_time,
            t.spans_midnight,t.break_minutes,t.recognized_minutes,t.target_credit_minutes
            FROM roster_slots s JOIN shift_types t ON t.shift_type_id=s.shift_type_id
            WHERE s.roster_id=? AND s.nurse_id IS NOT NULL""",
        { rs, _ ->
            val duration = ShiftTypeService.durationMinutes(
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(), rs.getBoolean("spans_midnight"),
            )
            // Same defaults as the shift type API: recognized = duration - break, credit = recognized.
            val recognized = rs.getObject("recognized_minutes") as Int? ?: (duration - rs.getInt("break_minutes")).coerceAtLeast(1)
            val credit = rs.getObject("target_credit_minutes") as Int? ?: recognized
            HourShift(
                UUID.fromString(rs.getString("nurse_id")), rs.getDate("shift_date").toLocalDate(),
                ShiftKind.valueOf(rs.getString("kind")), Instant.parse(rs.getString("starts_at")),
                Instant.parse(rs.getString("ends_at")), recognized, credit,
            )
        }, rosterId,
    )

    fun absences(rosterId: UUID): List<HourAbsence> = jdbc.query(
        "SELECT nurse_id,kind,starts_at,ends_at FROM approved_absences WHERE roster_id=?",
        { rs, _ ->
            HourAbsence(
                UUID.fromString(rs.getString("nurse_id")), ApprovedAbsenceKind.valueOf(rs.getString("kind")),
                Instant.parse(rs.getString("starts_at")), Instant.parse(rs.getString("ends_at")),
            )
        }, rosterId,
    )

    fun restDays(rosterId: UUID): List<HourRestDay> = jdbc.query(
        "SELECT nurse_id,rest_date,kind FROM roster_rest_days WHERE roster_id=?",
        { rs, _ ->
            HourRestDay(UUID.fromString(rs.getString("nurse_id")), rs.getDate("rest_date").toLocalDate(), RestDayKind.valueOf(rs.getString("kind")))
        }, rosterId,
    )
}
