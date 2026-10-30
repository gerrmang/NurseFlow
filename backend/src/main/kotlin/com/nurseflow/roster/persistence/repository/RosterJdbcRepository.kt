package com.nurseflow.roster.persistence.repository

import com.nurseflow.common.error.ApiException
import com.nurseflow.roster.api.dto.response.ApprovedAbsenceView
import com.nurseflow.roster.api.dto.response.GenerationJob
import com.nurseflow.roster.api.dto.response.RuleDiagnostic
import com.nurseflow.roster.api.dto.response.StaffingSummary
import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.persistence.entity.DbNurse
import com.nurseflow.roster.persistence.entity.DbRoster
import com.nurseflow.roster.persistence.entity.DbSlot
import com.nurseflow.roster.persistence.entity.NewSlot
import com.nurseflow.roster.persistence.entity.ShiftDefinition
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Repository
class RosterJdbcRepository(
    private val jdbc: JdbcTemplate,
    private val mapper: JsonMapper,
) {
    private val strings = object : TypeReference<List<String>>() {}

    // ── Roster ──

    fun findRoster(id: UUID): DbRoster? = jdbc.query(
        "SELECT r.*,w.time_zone FROM rosters r JOIN wards w ON w.ward_id=r.ward_id WHERE r.roster_id=?",
        { rs, _ -> mapRoster(rs) }, id,
    ).firstOrNull()

    fun listRosterIds(wardId: UUID, month: String): List<UUID> = jdbc.query(
        "SELECT roster_id FROM rosters WHERE ward_id=? AND roster_month=? ORDER BY status, revision DESC",
        { rs, _ -> uuid(rs, "roster_id") }, wardId, month,
    )

    fun draftExistsForMonth(wardId: UUID, month: String): Boolean =
        (jdbc.queryForObject(
            "SELECT COUNT(*) FROM rosters WHERE ward_id=? AND roster_month=? AND status='DRAFT'",
            Int::class.java, wardId, month,
        ) ?: 0) > 0

    fun insertRoster(id: UUID, wardId: UUID, month: String) {
        jdbc.update(
            "INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,'DRAFT',FALSE,1)",
            id, wardId, month,
        )
    }

    fun activePublished(wardId: UUID, month: String): DbRoster? = jdbc.query(
        """SELECT r.*,w.time_zone FROM rosters r JOIN wards w ON w.ward_id=r.ward_id
            WHERE r.ward_id=? AND r.roster_month=? AND r.status='PUBLISHED' AND r.active=TRUE""",
        { rs, _ -> mapRoster(rs) }, wardId, month,
    ).firstOrNull()

    fun deactivatePublished(wardId: UUID, month: String) {
        jdbc.update("UPDATE rosters SET active=FALSE WHERE ward_id=? AND roster_month=? AND active=TRUE", wardId, month)
    }

    fun publishRoster(rosterId: UUID, publishedAt: Instant, confirmedBy: String) {
        jdbc.update(
            "UPDATE rosters SET status='PUBLISHED',active=TRUE,revision=revision+1,published_at=?,confirmed_by=? WHERE roster_id=?",
            publishedAt.toString(), confirmedBy, rosterId,
        )
    }

    fun increment(id: UUID) {
        jdbc.update("UPDATE rosters SET revision=revision+1 WHERE roster_id=?", id)
    }

    fun lockRoster(id: UUID) {
        jdbc.query("SELECT roster_id FROM rosters WHERE roster_id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, id)
    }

    // ── Ward ──

    fun lockWard(wardId: UUID) {
        if (jdbc.query(
                "SELECT ward_id FROM wards WHERE ward_id=? FOR UPDATE",
                { rs, _ -> rs.getString(1) }, wardId,
            ).isEmpty()
        ) {
            fail(HttpStatus.NOT_FOUND, "WARD_NOT_FOUND", "병동을 찾을 수 없습니다.")
        }
    }

    fun memberships(wardId: UUID, subject: String): Set<String> = jdbc.query(
        "SELECT role FROM ward_memberships WHERE ward_id=? AND subject=?",
        { rs, _ -> rs.getString("role") }, wardId, subject,
    ).toSet()

    fun canViewWardCalendar(wardId: UUID, subject: String): Boolean =
        (jdbc.queryForObject(
            "SELECT COUNT(*) FROM ward_memberships WHERE ward_id=? AND subject=? AND can_view_ward_calendar=TRUE",
            Int::class.java, wardId, subject,
        ) ?: 0) > 0

    // ── Slots ──

    fun slots(rosterId: UUID): List<DbSlot> = jdbc.query(
        """SELECT s.*,t.code,t.kind,n.display_name FROM roster_slots s
            JOIN shift_types t ON t.shift_type_id=s.shift_type_id
            LEFT JOIN nurses n ON n.nurse_id=s.nurse_id
            WHERE s.roster_id=? ORDER BY s.shift_date,s.starts_at,s.slot_id""",
        { rs, _ ->
            DbSlot(
                uuid(rs, "slot_id"), uuid(rs, "roster_id"), rs.getDate("shift_date").toLocalDate(),
                uuid(rs, "shift_type_id"), rs.getString("code"), ShiftKind.valueOf(rs.getString("kind")),
                Instant.parse(rs.getString("starts_at")), Instant.parse(rs.getString("ends_at")),
                mapper.readValue(rs.getString("required_qualifications"), strings).toSet(),
                rs.getString("nurse_id")?.let(UUID::fromString), rs.getString("display_name"), rs.getBoolean("locked"),
            )
        },
        rosterId,
    )

    fun deleteSlots(rosterId: UUID) {
        jdbc.update("DELETE FROM roster_slots WHERE roster_id=?", rosterId)
    }

    fun insertSlot(rosterId: UUID, slot: NewSlot) {
        jdbc.update(
            """INSERT INTO roster_slots(slot_id,roster_id,shift_date,shift_type_id,starts_at,ends_at,required_qualifications)
                VALUES(?,?,?,?,?,?,?)""",
            UUID.randomUUID(), rosterId, slot.date, slot.shiftTypeId,
            slot.startsAt.toString(), slot.endsAt.toString(),
            mapper.writeValueAsString(slot.requiredQualifications),
        )
    }

    fun assignSlot(slotId: UUID, rosterId: UUID, nurseId: UUID, locked: Boolean) {
        jdbc.update(
            "UPDATE roster_slots SET nurse_id=?,locked=? WHERE slot_id=? AND roster_id=?",
            nurseId, locked, slotId, rosterId,
        )
    }

    fun unassignSlot(slotId: UUID, rosterId: UUID): Boolean =
        jdbc.update(
            "UPDATE roster_slots SET nurse_id=NULL,locked=FALSE WHERE slot_id=? AND roster_id=?",
            slotId, rosterId,
        ) > 0

    fun clearUnlockedAssignments(rosterId: UUID) {
        jdbc.update("UPDATE roster_slots SET nurse_id=NULL,locked=FALSE WHERE roster_id=? AND locked=FALSE", rosterId)
    }

    fun clearAllAssignments(rosterId: UUID) {
        jdbc.update("UPDATE roster_slots SET nurse_id=NULL,locked=FALSE WHERE roster_id=?", rosterId)
    }

    // ── Shift Types ──

    fun findShiftDefinition(shiftTypeId: UUID, wardId: UUID): ShiftDefinition? = jdbc.query(
        "SELECT code,kind,start_time,end_time,spans_midnight FROM shift_types WHERE shift_type_id=? AND ward_id=? AND active=TRUE",
        { rs, _ ->
            ShiftDefinition(
                rs.getString("code"), ShiftKind.valueOf(rs.getString("kind")),
                rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(),
                rs.getBoolean("spans_midnight"),
            )
        }, shiftTypeId, wardId,
    ).firstOrNull()

    // ── Nurses ──

    fun findNurse(id: UUID, wardId: UUID): DbNurse? = jdbc.query(
        "SELECT nurse_id,qualifications,active FROM nurses WHERE nurse_id=? AND ward_id=?",
        { rs, _ ->
            DbNurse(
                uuid(rs, "nurse_id"),
                mapper.readValue(rs.getString("qualifications"), strings).toSet(),
                rs.getBoolean("active"),
            )
        }, id, wardId,
    ).firstOrNull()

    fun activeNurses(wardId: UUID): List<DbNurse> = jdbc.query(
        "SELECT nurse_id,qualifications,active FROM nurses WHERE ward_id=? AND active=TRUE",
        { rs, _ ->
            DbNurse(
                uuid(rs, "nurse_id"),
                mapper.readValue(rs.getString("qualifications"), strings).toSet(),
                rs.getBoolean("active"),
            )
        }, wardId,
    )

    fun activeNurseCount(wardId: UUID): Int =
        jdbc.queryForObject("SELECT COUNT(*) FROM nurses WHERE ward_id=? AND active=TRUE", Int::class.java, wardId) ?: 0

    fun findNurseIdBySubject(wardId: UUID, subject: String): UUID? = jdbc.query(
        "SELECT nurse_id FROM nurses WHERE ward_id=? AND subject=? AND active=TRUE",
        { rs, _ -> uuid(rs, "nurse_id") }, wardId, subject,
    ).firstOrNull()

    // ── Absences ──

    fun absences(rosterId: UUID): List<ApprovedAbsenceView> = jdbc.query(
        "SELECT absence_id,nurse_id,kind,starts_at,ends_at FROM approved_absences WHERE roster_id=? ORDER BY starts_at",
        { rs, _ ->
            ApprovedAbsenceView(
                uuid(rs, "absence_id"), uuid(rs, "nurse_id"), rs.getString("kind"),
                Instant.parse(rs.getString("starts_at")), Instant.parse(rs.getString("ends_at")),
            )
        }, rosterId,
    )

    fun insertAbsence(id: UUID, rosterId: UUID, nurseId: UUID, kind: String, startsAt: Instant, endsAt: Instant) {
        jdbc.update(
            "INSERT INTO approved_absences(absence_id,roster_id,nurse_id,kind,starts_at,ends_at) VALUES(?,?,?,?,?,?)",
            id, rosterId, nurseId, kind, startsAt.toString(), endsAt.toString(),
        )
    }

    fun updateAbsence(absenceId: UUID, rosterId: UUID, nurseId: UUID, kind: String, startsAt: Instant, endsAt: Instant) {
        jdbc.update(
            "UPDATE approved_absences SET nurse_id=?,kind=?,starts_at=?,ends_at=? WHERE absence_id=? AND roster_id=?",
            nurseId, kind, startsAt.toString(), endsAt.toString(), absenceId, rosterId,
        )
    }

    fun deleteAbsence(absenceId: UUID, rosterId: UUID): Boolean =
        jdbc.update("DELETE FROM approved_absences WHERE absence_id=? AND roster_id=?", absenceId, rosterId) > 0

    fun adjacentPublishedAbsences(wardId: UUID, month: String): List<ApprovedAbsenceView> = jdbc.query(
        """SELECT a.absence_id,a.nurse_id,a.kind,a.starts_at,a.ends_at FROM approved_absences a
            JOIN rosters r ON r.roster_id=a.roster_id
            WHERE r.ward_id=? AND r.active=TRUE AND r.status='PUBLISHED' AND r.roster_month<>?""",
        { rs, _ ->
            ApprovedAbsenceView(
                uuid(rs, "absence_id"), uuid(rs, "nurse_id"), rs.getString("kind"),
                Instant.parse(rs.getString("starts_at")), Instant.parse(rs.getString("ends_at")),
            )
        }, wardId, month,
    )

    fun adjacentPublishedRosterIds(wardId: UUID, month: String): List<UUID> = jdbc.query(
        "SELECT roster_id FROM rosters WHERE ward_id=? AND active=TRUE AND status='PUBLISHED' AND roster_month<>?",
        { rs, _ -> uuid(rs, "roster_id") }, wardId, month,
    )

    // ── Generation Jobs ──

    fun jobRosterId(jobId: UUID): UUID? = jdbc.query(
        "SELECT roster_id FROM generation_jobs WHERE job_id=?",
        { rs, _ -> uuid(rs, "roster_id") }, jobId,
    ).firstOrNull()

    fun readGenerationJob(jobId: UUID): GenerationJob? = jdbc.query(
        "SELECT * FROM generation_jobs WHERE job_id=?",
        { rs, _ ->
            GenerationJob(
                jobId = uuid(rs, "job_id"),
                rosterId = uuid(rs, "roster_id"),
                status = rs.getString("status"),
                sourceRosterRevision = rs.getInt("source_revision"),
                createdAt = Instant.parse(rs.getString("created_at")),
                progressPercent = when (rs.getString("status")) {
                    "QUEUED" -> 0
                    "RUNNING" -> null
                    else -> 100
                },
                resultRosterRevision = rs.getObject("result_revision")?.let { rs.getInt("result_revision") },
                staffing = rs.getString("staffing_json")?.let { mapper.readValue(it, StaffingSummary::class.java) },
                diagnostics = rs.getString("diagnostics_json")?.let {
                    mapper.readValue(it, object : TypeReference<List<RuleDiagnostic>>() {})
                } ?: emptyList(),
                finishedAt = rs.getString("finished_at")?.let(Instant::parse),
            )
        }, jobId,
    ).firstOrNull()

    fun insertGenerationJob(
        jobId: UUID, rosterId: UUID, requestedBy: String, sourceRevision: Int,
        preserveLocked: Boolean, timeLimitSeconds: Int, createdAt: Instant,
    ) {
        jdbc.update(
            """INSERT INTO generation_jobs(job_id,roster_id,requested_by,status,source_revision,
                preserve_locked,time_limit_seconds,created_at) VALUES(?,?,?,'QUEUED',?,?,?,?)""",
            jobId, rosterId, requestedBy, sourceRevision, preserveLocked, timeLimitSeconds, createdAt.toString(),
        )
    }

    fun pendingGenerationCount(rosterId: UUID): Int =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM generation_jobs WHERE roster_id=? AND status IN ('QUEUED','RUNNING')",
            Int::class.java, rosterId,
        ) ?: 0

    fun lockGenerationJob(jobId: UUID) {
        jdbc.query("SELECT job_id FROM generation_jobs WHERE job_id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, jobId)
    }

    fun cancelQueuedJob(jobId: UUID) {
        jdbc.update(
            "UPDATE generation_jobs SET status='CANCELLED',cancel_requested=TRUE,finished_at=? WHERE job_id=?",
            Instant.now().toString(), jobId,
        )
    }

    fun requestCancel(jobId: UUID) {
        jdbc.update("UPDATE generation_jobs SET cancel_requested=TRUE WHERE job_id=?", jobId)
    }

    fun markRunning(jobId: UUID): Boolean =
        jdbc.update(
            "UPDATE generation_jobs SET status='RUNNING' WHERE job_id=? AND status='QUEUED' AND cancel_requested=FALSE",
            jobId,
        ) == 1

    fun markFailed(jobId: UUID) {
        jdbc.update(
            """UPDATE generation_jobs SET status='FAILED',finished_at=?
                WHERE job_id=? AND status IN ('QUEUED','RUNNING')""",
            Instant.now().toString(), jobId,
        )
    }

    fun finishGeneration(
        jobId: UUID, status: String, resultRevision: Int?,
        staffing: StaffingSummary?, diagnostics: List<RuleDiagnostic>,
    ) {
        jdbc.update(
            """UPDATE generation_jobs SET status=?,result_revision=?,staffing_json=?,diagnostics_json=?,finished_at=?
                WHERE job_id=?""",
            status, resultRevision, staffing?.let(mapper::writeValueAsString),
            mapper.writeValueAsString(diagnostics), Instant.now().toString(), jobId,
        )
    }

    fun generationCancelled(jobId: UUID): Boolean =
        jdbc.queryForObject("SELECT cancel_requested FROM generation_jobs WHERE job_id=?", Boolean::class.java, jobId) == true

    fun timeLimitNanos(jobId: UUID): Long =
        (jdbc.queryForObject(
            "SELECT time_limit_seconds FROM generation_jobs WHERE job_id=?",
            Int::class.java, jobId,
        ) ?: 120).toLong() * 1_000_000_000L

    fun generationRequestedBy(jobId: UUID): String = jdbc.query(
        "SELECT requested_by FROM generation_jobs WHERE job_id=?",
        { rs, _ -> rs.getString(1) }, jobId,
    ).first()

    fun preserveLocked(jobId: UUID): Boolean =
        jdbc.queryForObject("SELECT preserve_locked FROM generation_jobs WHERE job_id=?", Boolean::class.java, jobId) == true

    // ── Idempotency Replay ──

    fun <T> replay(key: UUID, action: String, requestHash: String, type: Class<T>): T? {
        val saved = jdbc.query(
            "SELECT operation,request_hash,response_json FROM mutation_replays WHERE idempotency_key=?",
            { rs, _ -> Triple(rs.getString("operation"), rs.getString("request_hash"), rs.getString("response_json")) },
            key,
        ).firstOrNull() ?: return null
        if (saved.first != action || saved.second != requestHash) {
            fail(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "다른 요청에 이미 사용한 키입니다.")
        }
        return mapper.readValue(saved.third, type)
    }

    fun recordReplay(key: UUID, action: String, requestHash: String, response: Any) {
        jdbc.update(
            "INSERT INTO mutation_replays(idempotency_key,operation,request_hash,response_json) VALUES(?,?,?,?)",
            key, action, requestHash, mapper.writeValueAsString(response),
        )
    }

    // ── Shared ──

    private fun mapRoster(rs: ResultSet) = DbRoster(
        uuid(rs, "roster_id"), uuid(rs, "ward_id"),
        rs.getString("roster_month"), rs.getString("status"), rs.getInt("revision"),
        ZoneId.of(rs.getString("time_zone")),
        rs.getString("published_at")?.let(Instant::parse), rs.getString("confirmed_by"),
    )

    private fun uuid(rs: ResultSet, column: String): UUID = UUID.fromString(rs.getString(column))

    private fun fail(status: HttpStatus, code: String, detail: String): Nothing =
        throw ApiException(status, code, detail)
}
