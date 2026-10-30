package com.nurseflow.roster.application

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import com.nurseflow.roster.api.*
import com.nurseflow.roster.domain.ApprovedAbsence
import com.nurseflow.roster.domain.ApprovedAbsenceKind
import com.nurseflow.roster.domain.RosterRuleValidator
import com.nurseflow.roster.domain.RuleSeverity
import com.nurseflow.roster.domain.ShiftAssignment
import com.nurseflow.roster.domain.ShiftKind
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.security.MessageDigest
import java.util.UUID

private data class DbRoster(
    val id: UUID,
    val wardId: UUID,
    val month: String,
    val status: String,
    val revision: Int,
    val zone: ZoneId,
    val publishedAt: Instant?,
    val confirmedBy: String?,
)

private data class DbSlot(
    val id: UUID,
    val rosterId: UUID,
    val date: LocalDate,
    val shiftTypeId: UUID,
    val shiftCode: String,
    val kind: ShiftKind,
    val startsAt: Instant,
    val endsAt: Instant,
    val requiredQualifications: Set<String>,
    val nurseId: UUID?,
    val nurseName: String?,
    val locked: Boolean,
) {
    fun assignment(): ShiftAssignment? = nurseId?.let {
        ShiftAssignment(id, it, date, kind, startsAt, endsAt)
    }
}

private data class DbNurse(val id: UUID, val qualifications: Set<String>, val active: Boolean)

@Service
class RosterService(
    private val jdbc: JdbcTemplate,
    private val mapper: JsonMapper,
) {
    private val validator = RosterRuleValidator()
    private val strings = object : TypeReference<List<String>>() {}

    fun etag(rosterId: UUID, revision: Int): String = "roster-$rosterId-r$revision"

    fun currentEtag(rosterId: UUID): String {
        val roster = findRoster(rosterId) ?: fail(HttpStatus.NOT_FOUND, "ROSTER_NOT_FOUND", "근무표를 찾을 수 없습니다.")
        return etag(roster.id, roster.revision)
    }

    @Transactional(readOnly = true)
    fun listRosters(wardId: UUID, month: String, subject: String): List<RosterSummary> {
        parseMonth(month)
        requireAdmin(wardId, subject)
        return jdbc.query(
            "SELECT roster_id FROM rosters WHERE ward_id=? AND roster_month=? ORDER BY status, revision DESC",
            { rs, _ -> uuid(rs, "roster_id") }, wardId, month,
        ).map { summary(findRoster(it)!!) }
    }

    @Transactional
    fun createRoster(wardId: UUID, request: CreateRosterRequest, subject: String, idempotencyKey: UUID): RosterView {
        parseMonth(request.month)
        requireAdmin(wardId, subject)
        lockWard(wardId)
        val action = "create-roster:$wardId"
        replay(idempotencyKey, action, request, RosterView::class.java)?.let { return it }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM rosters WHERE ward_id=? AND roster_month=? AND status='DRAFT'",
                Int::class.java, wardId, request.month)!! > 0) {
            fail(HttpStatus.CONFLICT, "DRAFT_EXISTS", "해당 월의 초안이 이미 있습니다.")
        }
        val publishedAbsences = activePublished(wardId, request.month)?.let { absences(it.id) }.orEmpty()
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO rosters(roster_id,ward_id,roster_month,status,active,revision) VALUES(?,?,?,'DRAFT',FALSE,1)",
            id, wardId, request.month)
        publishedAbsences.forEach { absence ->
            jdbc.update("INSERT INTO approved_absences(absence_id,roster_id,nurse_id,kind,starts_at,ends_at) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID(), id, absence.nurseId, absence.kind,
                absence.startsAt.toString(), absence.endsAt.toString())
        }
        return view(findRoster(id)!!).also { recordReplay(idempotencyKey, action, request, it) }
    }

    @Transactional(readOnly = true)
    fun getRoster(rosterId: UUID, subject: String): RosterView {
        val roster = visibleToAdmin(rosterId, subject)
        return view(roster)
    }

    @Transactional
    fun replaceRequirements(
        rosterId: UUID, ifMatch: String, request: ReplaceRequirementsRequest, subject: String,
    ): RosterSummary {
        val roster = lockedDraft(rosterId, ifMatch, subject)
        if (request.items.size > 1000 || request.items.any { it.requiredCount !in 0..100 ||
                    it.requiredQualifications.size > 50 || it.requiredQualifications.any { q -> q.length !in 1..64 } }) {
            fail(HttpStatus.BAD_REQUEST, "INVALID_REQUIREMENTS", "인력 수요 항목이 허용 범위를 벗어났습니다.")
        }
        if (request.items.map { it.date to it.shiftTypeId }.toSet().size != request.items.size) {
            fail(HttpStatus.BAD_REQUEST, "DUPLICATE_REQUIREMENT", "날짜와 근무 유형 조합이 중복되었습니다.")
        }
        if (request.items.sumOf { it.requiredCount } > 10000) {
            fail(HttpStatus.BAD_REQUEST, "TOO_MANY_SLOTS", "생성할 근무 칸이 너무 많습니다.")
        }
        if (slots(roster.id).any { it.nurseId != null }) {
            fail(HttpStatus.CONFLICT, "ASSIGNMENTS_EXIST", "배정을 해제한 뒤 인력 수요를 변경해 주세요.")
        }
        val month = parseMonth(roster.month)
        val newSlots = request.items.flatMap { item ->
            if (YearMonth.from(item.date) != month) {
                fail(HttpStatus.BAD_REQUEST, "DATE_OUTSIDE_ROSTER", "근무 날짜가 해당 월에 속하지 않습니다.")
            }
            val shift = jdbc.query(
                "SELECT code,kind,start_time,end_time,spans_midnight FROM shift_types WHERE shift_type_id=? AND ward_id=? AND active=TRUE",
                { rs, _ -> ShiftDefinition(rs.getString("code"), ShiftKind.valueOf(rs.getString("kind")),
                    rs.getTime("start_time").toLocalTime(), rs.getTime("end_time").toLocalTime(),
                    rs.getBoolean("spans_midnight")) }, item.shiftTypeId, roster.wardId,
            ).firstOrNull() ?: fail(HttpStatus.BAD_REQUEST, "SHIFT_TYPE_NOT_FOUND", "근무 유형을 찾을 수 없습니다.")
            val start = item.date.atTime(shift.start).atZone(roster.zone).toInstant()
            val endDate = if (shift.spansMidnight) item.date.plusDays(1) else item.date
            val end = endDate.atTime(shift.end).atZone(roster.zone).toInstant()
            if (!start.isBefore(end)) {
                fail(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_SHIFT_INTERVAL", "근무 종료 시각은 시작 시각보다 뒤여야 합니다.")
            }
            List(item.requiredCount) { NewSlot(item.date, item.shiftTypeId, start, end,
                item.requiredQualifications.distinct()) }
        }
        jdbc.update("DELETE FROM roster_slots WHERE roster_id=?", roster.id)
        newSlots.forEach { slot ->
            jdbc.update("""INSERT INTO roster_slots(slot_id,roster_id,shift_date,shift_type_id,starts_at,ends_at,required_qualifications)
                VALUES(?,?,?,?,?,?,?)""", UUID.randomUUID(), roster.id, slot.date, slot.shiftTypeId,
                slot.startsAt.toString(), slot.endsAt.toString(), mapper.writeValueAsString(slot.requiredQualifications))
        }
        increment(roster.id)
        return summary(findRoster(roster.id)!!)
    }

    @Transactional(readOnly = true)
    fun listAbsences(rosterId: UUID, subject: String): List<ApprovedAbsenceView> {
        visibleToAdmin(rosterId, subject)
        return absences(rosterId)
    }

    @Transactional
    fun addAbsence(
        rosterId: UUID, ifMatch: String, idempotencyKey: UUID, input: ApprovedAbsenceInput, subject: String,
    ): AbsenceMutation {
        val roster = lockAuthorizedRoster(rosterId, subject)
        val action = "add-absence:$rosterId"
        val payload = ifMatch to input
        replay(idempotencyKey, action, payload, AbsenceMutation::class.java)?.let { return it }
        checkRevision(roster, ifMatch)
        ensureDraft(roster)
        validateAbsence(roster, input)
        val id = UUID.randomUUID()
        jdbc.update("INSERT INTO approved_absences(absence_id,roster_id,nurse_id,kind,starts_at,ends_at) VALUES(?,?,?,?,?,?)",
            id, roster.id, input.nurseId, input.kind, input.startsAt.toString(), input.endsAt.toString())
        increment(roster.id)
        val result = AbsenceMutation(ApprovedAbsenceView(id, input.nurseId, input.kind, input.startsAt, input.endsAt),
            roster.revision + 1)
        recordReplay(idempotencyKey, action, payload, result)
        return result
    }

    @Transactional
    fun updateAbsence(
        rosterId: UUID, absenceId: UUID, ifMatch: String, input: ApprovedAbsenceInput, subject: String,
    ): AbsenceMutation {
        val roster = lockedDraft(rosterId, ifMatch, subject)
        if (absences(roster.id).none { it.absenceId == absenceId }) {
            fail(HttpStatus.NOT_FOUND, "ABSENCE_NOT_FOUND", "부재 일정을 찾을 수 없습니다.")
        }
        validateAbsence(roster, input)
        jdbc.update("UPDATE approved_absences SET nurse_id=?,kind=?,starts_at=?,ends_at=? WHERE absence_id=? AND roster_id=?",
            input.nurseId, input.kind, input.startsAt.toString(), input.endsAt.toString(), absenceId, roster.id)
        increment(roster.id)
        return AbsenceMutation(
            ApprovedAbsenceView(absenceId, input.nurseId, input.kind, input.startsAt, input.endsAt),
            roster.revision + 1,
        )
    }

    @Transactional
    fun deleteAbsence(rosterId: UUID, absenceId: UUID, ifMatch: String, subject: String): Int {
        val roster = lockedDraft(rosterId, ifMatch, subject)
        if (jdbc.update("DELETE FROM approved_absences WHERE absence_id=? AND roster_id=?", absenceId, roster.id) == 0) {
            fail(HttpStatus.NOT_FOUND, "ABSENCE_NOT_FOUND", "부재 일정을 찾을 수 없습니다.")
        }
        increment(roster.id)
        return roster.revision + 1
    }

    @Transactional(readOnly = true)
    fun validate(rosterId: UUID, ifMatch: String, input: ValidateAssignmentRequest, subject: String): AssignmentValidation {
        val roster = visibleToAdmin(rosterId, subject)
        checkRevision(roster, ifMatch)
        if (roster.status != "DRAFT") fail(HttpStatus.CONFLICT, "NOT_DRAFT", "확정된 근무표는 수정할 수 없습니다.")
        val slot = slots(roster.id).firstOrNull { it.id == input.slotId }
            ?: fail(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "근무 칸을 찾을 수 없습니다.")
        val violations = candidateViolations(roster, slot, input.nurseId)
        return AssignmentValidation(violations.isEmpty(), violations, rosterRevision = roster.revision)
    }

    @Transactional
    fun assign(
        rosterId: UUID, slotId: UUID, ifMatch: String, idempotencyKey: UUID,
        input: AssignNurseRequest, subject: String,
    ): RosterView {
        val roster = lockAuthorizedRoster(rosterId, subject)
        val action = "assign:$rosterId:$slotId"
        val payload = ifMatch to input
        replay(idempotencyKey, action, payload, RosterView::class.java)?.let { return it }
        checkRevision(roster, ifMatch)
        ensureDraft(roster)
        val slot = slots(roster.id).firstOrNull { it.id == slotId }
            ?: fail(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "근무 칸을 찾을 수 없습니다.")
        val violations = candidateViolations(roster, slot, input.nurseId)
        if (violations.isNotEmpty()) {
            throw RosterApiException(HttpStatus.UNPROCESSABLE_CONTENT, "RULE_VIOLATION", "배치할 수 없는 근무 칸입니다.",
                ruleViolations = violations)
        }
        jdbc.update("UPDATE roster_slots SET nurse_id=?,locked=? WHERE slot_id=? AND roster_id=?",
            input.nurseId, input.locked, slotId, roster.id)
        increment(roster.id)
        return view(findRoster(roster.id)!!).also { recordReplay(idempotencyKey, action, payload, it) }
    }

    @Transactional
    fun unassign(rosterId: UUID, slotId: UUID, ifMatch: String, subject: String): Int {
        val roster = lockedDraft(rosterId, ifMatch, subject)
        if (jdbc.update("UPDATE roster_slots SET nurse_id=NULL,locked=FALSE WHERE slot_id=? AND roster_id=?",
                slotId, roster.id) == 0) {
            fail(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "근무 칸을 찾을 수 없습니다.")
        }
        increment(roster.id)
        return roster.revision + 1
    }

    @Transactional
    fun confirm(
        rosterId: UUID, ifMatch: String, idempotencyKey: UUID,
        request: ConfirmRosterRequest, subject: String,
    ): RosterSummary {
        val roster = lockAuthorizedRoster(rosterId, subject)
        val action = "confirm:$rosterId"
        val payload = ifMatch to request
        replay(idempotencyKey, action, payload, RosterSummary::class.java)?.let { return it }
        checkRevision(roster, ifMatch)
        ensureDraft(roster)
        val currentSlots = slots(roster.id)
        if (currentSlots.isEmpty()) {
            fail(HttpStatus.UNPROCESSABLE_CONTENT, "ROSTER_REQUIREMENTS_REQUIRED", "필요 인원을 설정한 뒤 확정해 주세요.")
        }
        val unfilled = currentSlots.count { it.nurseId == null }
        if (unfilled > 0 && request.acknowledgeUnfilledCount != unfilled) {
            fail(HttpStatus.UNPROCESSABLE_CONTENT, "UNFILLED_ACK_REQUIRED", "미충원 인원 수를 확인해 주세요.")
        }
        val context = loadValidationContext(roster, currentSlots)
        val nursesById = mutableMapOf<UUID, DbNurse?>()
        val violations = currentSlots.filter { it.nurseId != null }.flatMap { slot ->
            candidateViolations(roster, slot, slot.nurseId!!, context, nursesById)
        }.plus(draftAbsenceConflicts(roster)).distinctBy { it.code to it.affectedDate }
        if (violations.isNotEmpty()) {
            throw RosterApiException(HttpStatus.UNPROCESSABLE_CONTENT, "RULE_VIOLATION",
                "확정 전에 근무 배치를 수정해 주세요.", ruleViolations = violations)
        }
        jdbc.update("UPDATE rosters SET active=FALSE WHERE ward_id=? AND roster_month=? AND active=TRUE",
            roster.wardId, roster.month)
        jdbc.update("UPDATE rosters SET status='PUBLISHED',active=TRUE,revision=revision+1,published_at=?,confirmed_by=? WHERE roster_id=?",
            Instant.now().toString(), subject, roster.id)
        return summary(findRoster(roster.id)!!).also { recordReplay(idempotencyKey, action, payload, it) }
    }

    @Transactional
    fun startGeneration(
        rosterId: UUID, ifMatch: String, idempotencyKey: UUID,
        request: StartGenerationRequest, subject: String,
    ): GenerationJob {
        val roster = lockAuthorizedRoster(rosterId, subject)
        val action = "start-generation:$rosterId"
        val payload = ifMatch to request
        replay(idempotencyKey, action, payload, GenerationJob::class.java)?.let { return it }
        checkRevision(roster, ifMatch)
        ensureDraft(roster)
        if (request.timeLimitSeconds !in 1..600) {
            fail(HttpStatus.BAD_REQUEST, "INVALID_TIME_LIMIT", "자동배치 제한 시간은 1~600초여야 합니다.")
        }
        val pending = jdbc.queryForObject(
            "SELECT COUNT(*) FROM generation_jobs WHERE roster_id=? AND status IN ('QUEUED','RUNNING')",
            Int::class.java, rosterId,
        ) ?: 0
        if (pending > 0) fail(HttpStatus.TOO_MANY_REQUESTS, "GENERATION_ALREADY_RUNNING", "자동배치가 이미 진행 중입니다.")
        val all = slots(rosterId)
        if (all.size > 2000) fail(HttpStatus.UNPROCESSABLE_CONTENT, "GENERATION_TOO_LARGE", "자동배치 한도는 2000칸입니다.")
        val staffCount = jdbc.queryForObject("SELECT COUNT(*) FROM nurses WHERE ward_id=? AND active=TRUE",
            Int::class.java, roster.wardId) ?: 0
        if (staffCount > 200) fail(HttpStatus.UNPROCESSABLE_CONTENT, "GENERATION_TOO_LARGE", "자동배치 한도는 활성 간호사 200명입니다.")
        val job = GenerationJob(UUID.randomUUID(), rosterId, "QUEUED", roster.revision, Instant.now(), progressPercent = 0)
        jdbc.update("""INSERT INTO generation_jobs(job_id,roster_id,requested_by,status,source_revision,
            preserve_locked,time_limit_seconds,created_at) VALUES(?,?,?,'QUEUED',?,?,?,?)""",
            job.jobId, rosterId, subject, roster.revision, request.preserveLockedAssignments,
            request.timeLimitSeconds, job.createdAt.toString())
        recordReplay(idempotencyKey, action, payload, job)
        return job
    }

    @Transactional(readOnly = true)
    fun getGenerationJob(jobId: UUID, subject: String): GenerationJob {
        val rosterId = jobRosterId(jobId)
        visibleToAdmin(rosterId, subject)
        return readGenerationJob(jobId)
    }

    @Transactional
    fun cancelGeneration(jobId: UUID, idempotencyKey: UUID, subject: String): GenerationJob {
        val rosterId = jobRosterId(jobId)
        val roster = visibleToAdmin(rosterId, subject)
        val action = "cancel-generation:$jobId"
        replay(idempotencyKey, action, jobId, GenerationJob::class.java)?.let { return it }
        jdbc.query("SELECT job_id FROM generation_jobs WHERE job_id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, jobId)
        val job = readGenerationJob(jobId)
        if (job.status !in setOf("QUEUED", "RUNNING")) {
            fail(HttpStatus.CONFLICT, "GENERATION_NOT_CANCELLABLE", "이미 종료된 자동배치 작업입니다.")
        }
        if (job.status == "QUEUED") {
            jdbc.update("UPDATE generation_jobs SET status='CANCELLED',cancel_requested=TRUE,finished_at=? WHERE job_id=?",
                Instant.now().toString(), jobId)
        } else {
            jdbc.update("UPDATE generation_jobs SET cancel_requested=TRUE WHERE job_id=?", jobId)
        }
        val result = readGenerationJob(jobId)
        recordReplay(idempotencyKey, action, jobId, result)
        return result
    }

    @Transactional
    fun markGenerationRunning(jobId: UUID): Boolean = jdbc.update(
        "UPDATE generation_jobs SET status='RUNNING' WHERE job_id=? AND status='QUEUED' AND cancel_requested=FALSE",
        jobId,
    ) == 1

    @Transactional
    fun failGeneration(jobId: UUID) {
        jdbc.update("""UPDATE generation_jobs SET status='FAILED',finished_at=?
            WHERE job_id=? AND status IN ('QUEUED','RUNNING')""", Instant.now().toString(), jobId)
    }

    @Transactional
    fun processGeneration(jobId: UUID) {
        val initial = readGenerationJob(jobId)
        if (initial.status != "RUNNING") return
        val requestedBy = jdbc.query("SELECT requested_by FROM generation_jobs WHERE job_id=?",
            { rs, _ -> rs.getString(1) }, jobId).first()
        val roster = lockAuthorizedRoster(initial.rosterId, requestedBy)
        if (roster.status != "DRAFT" || roster.revision != initial.sourceRosterRevision) {
            finishGeneration(jobId, "FAILED", null, null,
                listOf(RuleDiagnostic("STALE_ROSTER_REVISION", "HARD", "근무표가 변경되어 자동배치를 적용하지 않았습니다.")))
            return
        }
        val allSlots = slots(roster.id)
        val preserveLocked = jdbc.queryForObject("SELECT preserve_locked FROM generation_jobs WHERE job_id=?",
            Boolean::class.java, jobId) == true
        val preserved = allSlots.filter { it.nurseId != null && it.locked && preserveLocked }
        val removedIds = allSlots.filter { it.nurseId != null && it !in preserved }.map { it.id }.toSet()
        val context = loadValidationContext(roster, allSlots)
        val assignments = context.assignments.filterNot { it.assignmentId in removedIds }.toMutableList()
        val nurses = jdbc.query("SELECT nurse_id,qualifications,active FROM nurses WHERE ward_id=? AND active=TRUE",
            { rs, _ -> DbNurse(uuid(rs, "nurse_id"), mapper.readValue(rs.getString("qualifications"), strings).toSet(),
                rs.getBoolean("active")) }, roster.wardId)
        val nurseMap = nurses.associateBy { it.id }
        val preexistingViolations = draftAbsenceConflicts(roster) + preserved.flatMap { slot ->
            val nurse = nurseMap[slot.nurseId]
            val candidate = slot.assignment()!!
            if (nurse == null || !nurse.qualifications.containsAll(slot.requiredQualifications)) {
                listOf(RuleDiagnostic("LOCKED_ASSIGNMENT_INVALID", "HARD", "고정 배치가 현재 자격 또는 활성 상태와 맞지 않습니다.", slot.date))
            } else {
                validator.validate(candidate, assignments, context.absences).map(RuleDiagnostic::from)
            }
        }
        if (preexistingViolations.isNotEmpty()) {
            finishGeneration(jobId, "FAILED", null, null, preexistingViolations.distinctBy { it.code to it.affectedDate })
            return
        }
        val counts = preserved.groupingBy { it.nurseId!! }.eachCount().toMutableMap()
        val nightCounts = preserved.filter { it.kind == ShiftKind.NIGHT }.groupingBy { it.nurseId!! }.eachCount().toMutableMap()
        val selected = mutableMapOf<UUID, UUID>()
        val deadline = System.nanoTime() + initialTimeLimitNanos(jobId)
        var timedOut = false
        var cancelled = false
        generationLoop@ for (slot in allSlots.filter { it !in preserved }.sortedWith(
            compareBy<DbSlot>({ it.date }, { it.startsAt }, { it.id.toString() }))) {
            if (generationCancelled(jobId)) { cancelled = true; break }
            if (System.nanoTime() >= deadline) { timedOut = true; break }
            val ordered = nurses.sortedWith(compareBy<DbNurse>(
                { counts[it.id] ?: 0 },
                { nightCounts[it.id] ?: 0 },
                { it.id.toString() },
            ))
            for (nurse in ordered) {
                if (System.nanoTime() >= deadline) { timedOut = true; break@generationLoop }
                if (!nurse.qualifications.containsAll(slot.requiredQualifications)) continue
                val candidate = ShiftAssignment(slot.id, nurse.id, slot.date, slot.kind, slot.startsAt, slot.endsAt)
                if (validator.validate(candidate, assignments, context.absences).isNotEmpty()) continue
                selected[slot.id] = nurse.id
                assignments += candidate
                counts[nurse.id] = (counts[nurse.id] ?: 0) + 1
                if (slot.kind == ShiftKind.NIGHT) nightCounts[nurse.id] = (nightCounts[nurse.id] ?: 0) + 1
                break
            }
        }
        if (cancelled || generationCancelled(jobId)) {
            finishGeneration(jobId, "CANCELLED", null, null, emptyList())
            return
        }
        if (preserveLocked) {
            jdbc.update("UPDATE roster_slots SET nurse_id=NULL,locked=FALSE WHERE roster_id=? AND locked=FALSE", roster.id)
        } else {
            jdbc.update("UPDATE roster_slots SET nurse_id=NULL,locked=FALSE WHERE roster_id=?", roster.id)
        }
        selected.forEach { (slotId, nurseId) ->
            jdbc.update("UPDATE roster_slots SET nurse_id=?,locked=FALSE WHERE slot_id=? AND roster_id=?",
                nurseId, slotId, roster.id)
        }
        val finalSlots = slots(roster.id)
        val finalContext = loadValidationContext(roster, finalSlots)
        val nurseCache = mutableMapOf<UUID, DbNurse?>()
        val finalViolations = finalSlots.filter { it.nurseId != null }.flatMap { slot ->
            candidateViolations(roster, slot, slot.nurseId!!, finalContext, nurseCache)
        } + draftAbsenceConflicts(roster)
        if (finalViolations.isNotEmpty()) {
            throw IllegalStateException("Automatic roster assignment failed final hard-rule validation")
        }
        increment(roster.id)
        val staffing = StaffingSummary(finalSlots.size, finalSlots.count { it.nurseId != null },
            finalSlots.count { it.nurseId == null })
        val diagnostics = finalSlots.filter { it.nurseId == null }.map {
            RuleDiagnostic("UNFILLED_SLOT", "SOFT", "현재 배치 순서와 필수 규칙으로 채우지 못한 칸입니다.", it.date)
        }.distinctBy { it.code to it.affectedDate } + if (timedOut) {
            listOf(RuleDiagnostic("TIME_LIMIT_REACHED", "SOFT", "제한 시간에 도달해 남은 칸은 검토가 필요합니다."))
        } else emptyList()
        val status = when {
            timedOut -> "TIMED_OUT"
            staffing.unfilled > 0 -> "PARTIAL"
            else -> "COMPLETED"
        }
        finishGeneration(jobId, status, roster.revision + 1, staffing, diagnostics)
    }

    @Transactional(readOnly = true)
    fun publishedWard(wardId: UUID, month: String, subject: String): PublishedWardCalendar {
        parseMonth(month)
        requireWardView(wardId, subject)
        val roster = activePublished(wardId, month)
            ?: fail(HttpStatus.NOT_FOUND, "PUBLISHED_ROSTER_NOT_FOUND", "공개된 근무표가 없습니다.")
        val view = view(roster)
        return PublishedWardCalendar(wardId, month, roster.revision, roster.zone.id, view.staffing, view.slots)
    }

    @Transactional(readOnly = true)
    fun publishedMine(wardId: UUID, month: String, subject: String): PublishedPersonalCalendar {
        parseMonth(month)
        requireMember(wardId, subject)
        val nurseId = jdbc.query("SELECT nurse_id FROM nurses WHERE ward_id=? AND subject=? AND active=TRUE",
            { rs, _ -> uuid(rs, "nurse_id") }, wardId, subject).firstOrNull()
            ?: fail(HttpStatus.FORBIDDEN, "NURSE_IDENTITY_REQUIRED", "본인 근무표를 조회할 수 없습니다.")
        val roster = activePublished(wardId, month)
            ?: fail(HttpStatus.NOT_FOUND, "PUBLISHED_ROSTER_NOT_FOUND", "공개된 근무표가 없습니다.")
        val own = slots(roster.id).filter { it.nurseId == nurseId }
            .map { PersonalAssignment(it.date, it.shiftCode, it.startsAt, it.endsAt) }
        return PublishedPersonalCalendar(wardId, month, roster.revision, roster.zone.id, own)
    }

    private fun candidateViolations(
        roster: DbRoster,
        slot: DbSlot,
        nurseId: UUID,
        context: ValidationContext = loadValidationContext(roster),
        nursesById: MutableMap<UUID, DbNurse?> = mutableMapOf(),
    ): List<RuleDiagnostic> {
        val nurse = nursesById.getOrPut(nurseId) { findNurse(nurseId, roster.wardId) }
            ?: fail(HttpStatus.NOT_FOUND, "NURSE_NOT_FOUND", "병동 간호사를 찾을 수 없습니다.")
        val extra = mutableListOf<RuleDiagnostic>()
        if (!nurse.active) extra += RuleDiagnostic("NURSE_INACTIVE", "HARD", "활성화되지 않은 간호사입니다.", slot.date)
        if (!nurse.qualifications.containsAll(slot.requiredQualifications)) {
            extra += RuleDiagnostic("QUALIFICATION_MISSING", "HARD", "필수 자격이 부족합니다.", slot.date)
        }
        val candidate = ShiftAssignment(slot.id, nurseId, slot.date, slot.kind, slot.startsAt, slot.endsAt)
        return extra + validator.validate(candidate, context.assignments, context.absences).map(RuleDiagnostic::from)
    }

    private fun jobRosterId(jobId: UUID): UUID = jdbc.query(
        "SELECT roster_id FROM generation_jobs WHERE job_id=?",
        { rs, _ -> uuid(rs, "roster_id") }, jobId,
    ).firstOrNull() ?: fail(HttpStatus.NOT_FOUND, "GENERATION_JOB_NOT_FOUND", "자동배치 작업을 찾을 수 없습니다.")

    private fun readGenerationJob(jobId: UUID): GenerationJob = jdbc.query(
        "SELECT * FROM generation_jobs WHERE job_id=?",
        { rs, _ -> GenerationJob(
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
            diagnostics = rs.getString("diagnostics_json")?.let { mapper.readValue(it,
                object : TypeReference<List<RuleDiagnostic>>() {}) } ?: emptyList(),
            finishedAt = rs.getString("finished_at")?.let(Instant::parse),
        ) }, jobId,
    ).firstOrNull() ?: fail(HttpStatus.NOT_FOUND, "GENERATION_JOB_NOT_FOUND", "자동배치 작업을 찾을 수 없습니다.")

    private fun finishGeneration(
        jobId: UUID,
        status: String,
        resultRevision: Int?,
        staffing: StaffingSummary?,
        diagnostics: List<RuleDiagnostic>,
    ) {
        jdbc.update("""UPDATE generation_jobs SET status=?,result_revision=?,staffing_json=?,diagnostics_json=?,finished_at=?
            WHERE job_id=?""", status, resultRevision, staffing?.let(mapper::writeValueAsString),
            mapper.writeValueAsString(diagnostics), Instant.now().toString(), jobId)
    }

    private fun generationCancelled(jobId: UUID): Boolean = jdbc.queryForObject(
        "SELECT cancel_requested FROM generation_jobs WHERE job_id=?", Boolean::class.java, jobId,
    ) == true

    private fun initialTimeLimitNanos(jobId: UUID): Long =
        (jdbc.queryForObject("SELECT time_limit_seconds FROM generation_jobs WHERE job_id=?",
            Int::class.java, jobId) ?: 120).toLong() * 1_000_000_000L

    private fun loadValidationContext(roster: DbRoster, ownSlots: List<DbSlot> = slots(roster.id)): ValidationContext {
        val adjacentPublished = jdbc.query(
            "SELECT roster_id FROM rosters WHERE ward_id=? AND active=TRUE AND status='PUBLISHED' AND roster_month<>?",
            { rs, _ -> uuid(rs, "roster_id") }, roster.wardId, roster.month,
        ).flatMap { slots(it) }
        val existing = (ownSlots + adjacentPublished).mapNotNull { it.assignment() }
        val adjacentAbsences = jdbc.query(
            """SELECT a.absence_id,a.nurse_id,a.kind,a.starts_at,a.ends_at FROM approved_absences a
                JOIN rosters r ON r.roster_id=a.roster_id
                WHERE r.ward_id=? AND r.active=TRUE AND r.status='PUBLISHED' AND r.roster_month<>?""",
            { rs, _ -> ApprovedAbsenceView(uuid(rs, "absence_id"), uuid(rs, "nurse_id"), rs.getString("kind"),
                Instant.parse(rs.getString("starts_at")), Instant.parse(rs.getString("ends_at"))) },
            roster.wardId, roster.month,
        )
        val approved = (absences(roster.id) + adjacentAbsences).map {
            ApprovedAbsence(it.nurseId, ApprovedAbsenceKind.valueOf(it.kind), it.startsAt, it.endsAt)
        }
        return ValidationContext(existing, approved)
    }

    private fun validateAbsence(roster: DbRoster, input: ApprovedAbsenceInput) {
        if (input.kind !in setOf("APPROVED_LEAVE", "APPROVED_HALF_DAY") || !input.startsAt.isBefore(input.endsAt)) {
            fail(HttpStatus.BAD_REQUEST, "INVALID_ABSENCE", "부재 유형 또는 시간 구간이 올바르지 않습니다.")
        }
        val month = parseMonth(roster.month)
        val monthStart = month.atDay(1).atStartOfDay(roster.zone).toInstant()
        val nextMonthStart = month.plusMonths(1).atDay(1).atStartOfDay(roster.zone).toInstant()
        if (!(input.startsAt < nextMonthStart && monthStart < input.endsAt)) {
            fail(HttpStatus.BAD_REQUEST, "ABSENCE_OUTSIDE_ROSTER", "부재 시간이 해당 월과 겹쳐야 합니다.")
        }
        if (findNurse(input.nurseId, roster.wardId) == null) {
            fail(HttpStatus.NOT_FOUND, "NURSE_NOT_FOUND", "병동 간호사를 찾을 수 없습니다.")
        }
        val adjacentPublished = jdbc.query(
            "SELECT roster_id FROM rosters WHERE ward_id=? AND active=TRUE AND status='PUBLISHED' AND roster_month<>?",
            { rs, _ -> uuid(rs, "roster_id") }, roster.wardId, roster.month,
        ).flatMap { slots(it) }
        val conflict = (slots(roster.id) + adjacentPublished).any { slot ->
            slot.nurseId == input.nurseId && input.startsAt < slot.endsAt && slot.startsAt < input.endsAt
        }
        if (conflict) {
            fail(HttpStatus.UNPROCESSABLE_CONTENT, "ABSENCE_ASSIGNMENT_OVERLAP", "기존 배정과 부재 시간이 겹칩니다.")
        }
    }

    private fun draftAbsenceConflicts(roster: DbRoster): List<RuleDiagnostic> {
        val adjacentPublished = jdbc.query(
            "SELECT roster_id FROM rosters WHERE ward_id=? AND active=TRUE AND status='PUBLISHED' AND roster_month<>?",
            { rs, _ -> uuid(rs, "roster_id") }, roster.wardId, roster.month,
        ).flatMap { slots(it) }
        return absences(roster.id).flatMap { absence ->
            adjacentPublished.filter { slot ->
                slot.nurseId == absence.nurseId && absence.startsAt < slot.endsAt && slot.startsAt < absence.endsAt
            }.map { slot ->
                RuleDiagnostic("APPROVED_ABSENCE_PUBLISHED_OVERLAP", "HARD",
                    "승인된 부재 시간과 다른 월의 공개 근무가 겹칩니다.", slot.date)
            }
        }
    }

    private fun findNurse(id: UUID, wardId: UUID): DbNurse? = jdbc.query(
        "SELECT nurse_id,qualifications,active FROM nurses WHERE nurse_id=? AND ward_id=?",
        { rs, _ -> DbNurse(uuid(rs, "nurse_id"), mapper.readValue(rs.getString("qualifications"), strings).toSet(),
            rs.getBoolean("active")) }, id, wardId,
    ).firstOrNull()

    private fun absences(rosterId: UUID): List<ApprovedAbsenceView> = jdbc.query(
        "SELECT absence_id,nurse_id,kind,starts_at,ends_at FROM approved_absences WHERE roster_id=? ORDER BY starts_at",
        { rs, _ -> ApprovedAbsenceView(uuid(rs, "absence_id"), uuid(rs, "nurse_id"), rs.getString("kind"),
            Instant.parse(rs.getString("starts_at")), Instant.parse(rs.getString("ends_at"))) }, rosterId,
    )

    private fun slots(rosterId: UUID): List<DbSlot> = jdbc.query(
        """SELECT s.*,t.code,t.kind,n.display_name FROM roster_slots s
            JOIN shift_types t ON t.shift_type_id=s.shift_type_id
            LEFT JOIN nurses n ON n.nurse_id=s.nurse_id
            WHERE s.roster_id=? ORDER BY s.shift_date,s.starts_at,s.slot_id""",
        { rs, _ -> DbSlot(uuid(rs, "slot_id"), uuid(rs, "roster_id"), rs.getDate("shift_date").toLocalDate(),
            uuid(rs, "shift_type_id"), rs.getString("code"), ShiftKind.valueOf(rs.getString("kind")),
            Instant.parse(rs.getString("starts_at")), Instant.parse(rs.getString("ends_at")),
            mapper.readValue(rs.getString("required_qualifications"), strings).toSet(),
            rs.getString("nurse_id")?.let(UUID::fromString), rs.getString("display_name"), rs.getBoolean("locked")) },
        rosterId,
    )

    private fun view(roster: DbRoster): RosterView {
        val all = slots(roster.id)
        return RosterView(roster.id, roster.wardId, roster.month, roster.status, roster.revision, roster.zone.id,
            StaffingSummary(all.size, all.count { it.nurseId != null }, all.count { it.nurseId == null }),
            all.map { SlotView(it.id, it.date, it.shiftTypeId, it.startsAt, it.endsAt,
                if (it.nurseId == null) "UNFILLED" else "FILLED",
                it.nurseId?.let { nurseId -> AssignmentView(nurseId, it.nurseName ?: "", it.locked) }) },
            roster.publishedAt)
    }

    private fun summary(roster: DbRoster): RosterSummary {
        val all = slots(roster.id)
        return RosterSummary(roster.id, roster.wardId, roster.month, roster.status, roster.revision, roster.zone.id,
            StaffingSummary(all.size, all.count { it.nurseId != null }, all.count { it.nurseId == null }),
            roster.publishedAt)
    }

    private fun findRoster(id: UUID): DbRoster? = jdbc.query(
        "SELECT r.*,w.time_zone FROM rosters r JOIN wards w ON w.ward_id=r.ward_id WHERE r.roster_id=?",
        { rs, _ -> mapRoster(rs) }, id,
    ).firstOrNull()

    private fun mapRoster(rs: ResultSet) = DbRoster(uuid(rs, "roster_id"), uuid(rs, "ward_id"),
        rs.getString("roster_month"), rs.getString("status"), rs.getInt("revision"), ZoneId.of(rs.getString("time_zone")),
        rs.getString("published_at")?.let(Instant::parse), rs.getString("confirmed_by"))

    private fun visibleToAdmin(id: UUID, subject: String): DbRoster {
        val roster = findRoster(id) ?: fail(HttpStatus.NOT_FOUND, "ROSTER_NOT_FOUND", "근무표를 찾을 수 없습니다.")
        requireAdmin(roster.wardId, subject)
        return roster
    }

    private fun lockAuthorizedRoster(id: UUID, subject: String): DbRoster {
        val roster = visibleToAdmin(id, subject)
        // All ward mutations lock the ward first, then their roster. This also
        // serializes adjacent-month publication before validation reads it.
        lockWard(roster.wardId)
        jdbc.query("SELECT roster_id FROM rosters WHERE roster_id=? FOR UPDATE", { rs, _ -> rs.getString(1) }, id)
        return findRoster(id)!!
    }

    private fun lockedDraft(id: UUID, ifMatch: String, subject: String): DbRoster =
        lockAuthorizedRoster(id, subject).also {
            checkRevision(it, ifMatch)
            ensureDraft(it)
        }

    private fun ensureDraft(roster: DbRoster) {
        if (roster.status != "DRAFT") fail(HttpStatus.CONFLICT, "NOT_DRAFT", "확정된 근무표는 수정할 수 없습니다.")
    }

    private fun <T> replay(key: UUID, action: String, payload: Any, type: Class<T>): T? {
        val saved = jdbc.query(
            "SELECT operation,request_hash,response_json FROM mutation_replays WHERE idempotency_key=?",
            { rs, _ -> Triple(rs.getString("operation"), rs.getString("request_hash"), rs.getString("response_json")) },
            key,
        ).firstOrNull() ?: return null
        if (saved.first != action || saved.second != requestHash(payload)) {
            fail(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", "다른 요청에 이미 사용한 키입니다.")
        }
        return mapper.readValue(saved.third, type)
    }

    private fun recordReplay(key: UUID, action: String, payload: Any, response: Any) {
        jdbc.update("INSERT INTO mutation_replays(idempotency_key,operation,request_hash,response_json) VALUES(?,?,?,?)",
            key, action, requestHash(payload), mapper.writeValueAsString(response))
    }

    private fun requestHash(payload: Any): String = MessageDigest.getInstance("SHA-256")
        .digest(mapper.writeValueAsBytes(payload))
        .joinToString("") { "%02x".format(it) }

    private fun checkRevision(roster: DbRoster, ifMatch: String) {
        if (ifMatch != "\"${etag(roster.id, roster.revision)}\"") {
            throw RosterApiException(HttpStatus.PRECONDITION_FAILED, "STALE_ROSTER_REVISION",
                "근무표가 변경되었습니다. 다시 불러와 주세요.", currentRevision = roster.revision)
        }
    }

    private fun requireAdmin(wardId: UUID, subject: String) {
        val roles = memberships(wardId, subject)
        if (roles.isEmpty()) fail(HttpStatus.NOT_FOUND, "WARD_NOT_FOUND", "병동을 찾을 수 없습니다.")
        if ("WARD_ADMIN" !in roles) fail(HttpStatus.FORBIDDEN, "WARD_ADMIN_REQUIRED", "관리자 권한이 필요합니다.")
    }

    private fun requireMember(wardId: UUID, subject: String) {
        if (memberships(wardId, subject).isEmpty()) {
            fail(HttpStatus.NOT_FOUND, "WARD_NOT_FOUND", "병동을 찾을 수 없습니다.")
        }
    }

    private fun requireWardView(wardId: UUID, subject: String) {
        requireMember(wardId, subject)
        if (jdbc.queryForObject("SELECT COUNT(*) FROM ward_memberships WHERE ward_id=? AND subject=? AND can_view_ward_calendar=TRUE",
                Int::class.java, wardId, subject) == 0) {
            fail(HttpStatus.FORBIDDEN, "WARD_CALENDAR_VIEW_REQUIRED", "병동 전체 근무표 조회 권한이 없습니다.")
        }
    }

    private fun memberships(wardId: UUID, subject: String): Set<String> = jdbc.query(
        "SELECT role FROM ward_memberships WHERE ward_id=? AND subject=?",
        { rs, _ -> rs.getString("role") }, wardId, subject,
    ).toSet()

    private fun lockWard(wardId: UUID) {
        if (jdbc.query("SELECT ward_id FROM wards WHERE ward_id=? FOR UPDATE",
                { rs, _ -> rs.getString(1) }, wardId).isEmpty()) {
            fail(HttpStatus.NOT_FOUND, "WARD_NOT_FOUND", "병동을 찾을 수 없습니다.")
        }
    }

    private fun activePublished(wardId: UUID, month: String): DbRoster? = jdbc.query(
        """SELECT r.*,w.time_zone FROM rosters r JOIN wards w ON w.ward_id=r.ward_id
            WHERE r.ward_id=? AND r.roster_month=? AND r.status='PUBLISHED' AND r.active=TRUE""",
        { rs, _ -> mapRoster(rs) }, wardId, month,
    ).firstOrNull()

    private fun increment(id: UUID) {
        jdbc.update("UPDATE rosters SET revision=revision+1 WHERE roster_id=?", id)
    }

    private fun parseMonth(value: String): YearMonth = try {
        if (!Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(value)) throw IllegalArgumentException()
        YearMonth.parse(value)
    } catch (_: Exception) {
        fail(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "월은 YYYY-MM 형식이어야 합니다.")
    }

    private fun uuid(rs: ResultSet, column: String): UUID = UUID.fromString(rs.getString(column))

    private fun fail(status: HttpStatus, code: String, detail: String): Nothing =
        throw RosterApiException(status, code, detail)

    private data class ShiftDefinition(
        val code: String,
        val kind: ShiftKind,
        val start: LocalTime,
        val end: LocalTime,
        val spansMidnight: Boolean,
    )

    private data class NewSlot(
        val date: LocalDate,
        val shiftTypeId: UUID,
        val startsAt: Instant,
        val endsAt: Instant,
        val requiredQualifications: List<String>,
    )

    private data class ValidationContext(
        val assignments: List<ShiftAssignment>,
        val absences: List<ApprovedAbsence>,
    )
}
