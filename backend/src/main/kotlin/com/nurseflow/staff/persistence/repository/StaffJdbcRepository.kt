package com.nurseflow.staff.persistence.repository

import com.nurseflow.staff.persistence.entity.DbStaff
import com.nurseflow.staff.persistence.entity.DbStaffAdjustment
import com.nurseflow.staff.persistence.entity.DbTeam
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class DbCareerPolicy(val thresholdMonths: List<Int>, val ranks: List<String>)

@Repository
class StaffJdbcRepository(private val jdbc: JdbcTemplate, private val mapper: JsonMapper) {
    private val strings = object : TypeReference<List<String>>() {}
    private val ints = object : TypeReference<List<Int>>() {}

    // ── Teams ──

    fun teams(wardId: UUID, includeInactive: Boolean = false): List<DbTeam> = jdbc.query(
        "SELECT * FROM ward_teams WHERE ward_id=?" + (if (includeInactive) "" else " AND active=TRUE") +
            " ORDER BY sort_order, name",
        { rs, _ -> mapTeam(rs) }, wardId,
    )

    fun findTeam(wardId: UUID, teamId: UUID): DbTeam? = jdbc.query(
        "SELECT * FROM ward_teams WHERE ward_id=? AND team_id=? AND active=TRUE",
        { rs, _ -> mapTeam(rs) }, wardId, teamId,
    ).firstOrNull()

    fun nextTeamOrder(wardId: UUID): Int =
        jdbc.queryForObject("SELECT COALESCE(MAX(sort_order),0)+1 FROM ward_teams WHERE ward_id=?", Int::class.java, wardId)!!

    fun insertTeam(team: DbTeam) {
        jdbc.update("INSERT INTO ward_teams(team_id,ward_id,name,sort_order,active) VALUES(?,?,?,?,TRUE)",
            team.id, team.wardId, team.name, team.sortOrder)
    }

    fun updateTeam(teamId: UUID, name: String, sortOrder: Int) {
        jdbc.update("UPDATE ward_teams SET name=?, sort_order=? WHERE team_id=?", name, sortOrder, teamId)
    }

    fun deactivateTeam(teamId: UUID) {
        jdbc.update("UPDATE ward_teams SET active=FALSE WHERE team_id=?", teamId)
    }

    fun activeNursesInTeam(teamId: UUID): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM nurses WHERE team_id=? AND active=TRUE", Int::class.java, teamId)!!

    // ── Career policy ──

    fun careerPolicy(wardId: UUID): DbCareerPolicy? = jdbc.query(
        "SELECT stage_threshold_months, ranks FROM ward_career_policies WHERE ward_id=?",
        { rs, _ ->
            DbCareerPolicy(mapper.readValue(rs.getString(1), ints), mapper.readValue(rs.getString(2), strings))
        },
        wardId,
    ).firstOrNull()

    fun saveCareerPolicy(wardId: UUID, thresholds: List<Int>, ranks: List<String>, actor: String, now: Instant) {
        val updated = jdbc.update(
            "UPDATE ward_career_policies SET stage_threshold_months=?, ranks=?, updated_by=?, updated_at=? WHERE ward_id=?",
            mapper.writeValueAsString(thresholds), mapper.writeValueAsString(ranks), actor, now.toString(), wardId,
        )
        if (updated == 0) {
            jdbc.update(
                "INSERT INTO ward_career_policies(ward_id,stage_threshold_months,ranks,updated_by,updated_at) VALUES(?,?,?,?,?)",
                wardId, mapper.writeValueAsString(thresholds), mapper.writeValueAsString(ranks), actor, now.toString(),
            )
        }
    }

    // ── Staff ──

    fun staff(wardId: UUID): List<DbStaff> = jdbc.query(
        "SELECT * FROM nurses WHERE ward_id=?", { rs, _ -> mapStaff(rs) }, wardId,
    )

    fun findStaff(wardId: UUID, nurseId: UUID): DbStaff? = jdbc.query(
        "SELECT * FROM nurses WHERE ward_id=? AND nurse_id=?", { rs, _ -> mapStaff(rs) }, wardId, nurseId,
    ).firstOrNull()

    fun findStaffBySubject(wardId: UUID, subject: String): DbStaff? = jdbc.query(
        "SELECT * FROM nurses WHERE ward_id=? AND subject=?", { rs, _ -> mapStaff(rs) }, wardId, subject,
    ).firstOrNull()

    fun insertStaff(id: UUID, wardId: UUID, displayName: String, qualifications: List<String>, active: Boolean, teamId: UUID?) {
        jdbc.update(
            "INSERT INTO nurses(nurse_id,ward_id,display_name,qualifications,active,team_id) VALUES(?,?,?,?,?,?)",
            id, wardId, displayName, mapper.writeValueAsString(qualifications), active, teamId,
        )
    }

    fun updateStaff(nurseId: UUID, displayName: String, qualifications: List<String>, active: Boolean) {
        jdbc.update("UPDATE nurses SET display_name=?, qualifications=?, active=? WHERE nurse_id=?",
            displayName, mapper.writeValueAsString(qualifications), active, nurseId)
    }

    /** A team change clears the position; the admin places the nurse in the new team's order. */
    fun assignTeam(nurseId: UUID, teamId: UUID?) {
        jdbc.update("UPDATE nurses SET team_id=?, team_order=NULL WHERE nurse_id=?", teamId, nurseId)
    }

    /** Serializes team ordering and team moves in one ward (same row lock as roster and membership changes). */
    fun lockWard(wardId: UUID) {
        jdbc.query("SELECT ward_id FROM wards WHERE ward_id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, wardId)
    }

    fun activeTeamMembers(teamId: UUID): List<DbStaff> = jdbc.query(
        "SELECT * FROM nurses WHERE team_id=? AND active=TRUE", { rs, _ -> mapStaff(rs) }, teamId,
    )

    fun setTeamOrder(nurseId: UUID, teamOrder: Int) {
        jdbc.update("UPDATE nurses SET team_order=? WHERE nurse_id=?", teamOrder, nurseId)
    }

    fun updateRosterSettings(nurseId: UUID, seniorEligible: Boolean, stageOverride: Int?) {
        jdbc.update("UPDATE nurses SET senior_eligible=?, stage_override=? WHERE nurse_id=?", seniorEligible, stageOverride, nurseId)
    }

    fun insertAdjustment(wardId: UUID, nurseId: UUID, kind: String, before: String?, after: String?, actor: String, at: Instant) {
        jdbc.update(
            """INSERT INTO staff_adjustments(adjustment_id,ward_id,nurse_id,kind,before_value,after_value,actor,at)
               VALUES(?,?,?,?,?,?,?,?)""",
            UUID.randomUUID(), wardId, nurseId, kind, before?.take(200), after?.take(200), actor, at.toString(),
        )
    }

    fun adjustments(wardId: UUID, nurseId: UUID): List<DbStaffAdjustment> = jdbc.query(
        """SELECT a.*, u.display_name AS actor_name FROM staff_adjustments a
           LEFT JOIN users u ON CAST(u.user_id AS VARCHAR(36)) = a.actor
           WHERE a.ward_id=? AND a.nurse_id=? ORDER BY a.at DESC, a.adjustment_id""",
        { rs, _ ->
            DbStaffAdjustment(
                id = rs.getObject("adjustment_id", UUID::class.java),
                kind = rs.getString("kind"),
                before = rs.getString("before_value"),
                after = rs.getString("after_value"),
                actor = rs.getString("actor"),
                actorName = rs.getString("actor_name"),
                at = Instant.parse(rs.getString("at")),
            )
        },
        wardId, nurseId,
    )

    /** Nurse input records the hospital joining month without changing admin-managed career history. */
    fun submitHospitalJoinMonth(nurseId: UUID, hospitalJoinedOn: String) {
        jdbc.update(
            "UPDATE nurses SET hospital_joined_on=? WHERE nurse_id=?",
            hospitalJoinedOn, nurseId,
        )
    }

    /** Confirms the submitted career start while preserving any existing administrator-set rank. */
    fun confirmCareerStart(nurseId: UUID, careerStartedOn: String, actor: String, now: Instant) {
        jdbc.update(
            """UPDATE nurses SET profile_submitted_at=COALESCE(profile_submitted_at, ?),
                   confirmed_career_started_on=?, confirmed_rank_name=rank_name, profile_confirmed_at=?, profile_confirmed_by=?
               WHERE nurse_id=?""",
            now.toString(), careerStartedOn, now.toString(), actor, nurseId,
        )
    }

    /** Copies the given values into the confirmed fields (and the submitted fields, for admin edits). */
    fun confirmProfile(nurseId: UUID, careerStartedOn: String, rank: String, actor: String, now: Instant) {
        jdbc.update(
            """UPDATE nurses SET career_started_on=?, rank_name=?,
                   profile_submitted_at=COALESCE(profile_submitted_at, ?),
                   confirmed_career_started_on=?, confirmed_rank_name=?, profile_confirmed_at=?, profile_confirmed_by=?
               WHERE nurse_id=?""",
            careerStartedOn, rank, now.toString(), careerStartedOn, rank, now.toString(), actor, nurseId,
        )
    }

    fun wardZone(wardId: UUID): String? = jdbc.query(
        "SELECT time_zone FROM wards WHERE ward_id=?", { rs, _ -> rs.getString(1) }, wardId,
    ).firstOrNull()

    private fun mapTeam(rs: ResultSet) = DbTeam(
        rs.getObject("team_id", UUID::class.java), rs.getObject("ward_id", UUID::class.java),
        rs.getString("name"), rs.getInt("sort_order"), rs.getBoolean("active"),
    )

    private fun mapStaff(rs: ResultSet) = DbStaff(
        id = rs.getObject("nurse_id", UUID::class.java),
        wardId = rs.getObject("ward_id", UUID::class.java),
        subject = rs.getString("subject"),
        displayName = rs.getString("display_name"),
        qualifications = mapper.readValue(rs.getString("qualifications"), strings),
        active = rs.getBoolean("active"),
        teamId = rs.getObject("team_id", UUID::class.java),
        hospitalJoinedOn = rs.getString("hospital_joined_on"),
        careerStartedOn = rs.getString("career_started_on"),
        rank = rs.getString("rank_name"),
        submittedAt = rs.getString("profile_submitted_at")?.let(Instant::parse),
        confirmedCareerStartedOn = rs.getString("confirmed_career_started_on"),
        confirmedRank = rs.getString("confirmed_rank_name"),
        confirmedAt = rs.getString("profile_confirmed_at")?.let(Instant::parse),
        teamOrder = rs.getObject("team_order") as Int?,
        seniorEligible = rs.getBoolean("senior_eligible"),
        stageOverride = rs.getObject("stage_override") as Int?,
    )
}
