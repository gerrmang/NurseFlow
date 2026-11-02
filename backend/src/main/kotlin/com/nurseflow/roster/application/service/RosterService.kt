package com.nurseflow.roster.application.service

import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper
import com.nurseflow.common.error.ApiException
import com.nurseflow.roster.api.dto.request.*
import com.nurseflow.roster.api.dto.response.*
import com.nurseflow.roster.domain.model.ApprovedAbsence
import com.nurseflow.roster.domain.model.ApprovedAbsenceKind
import com.nurseflow.roster.domain.model.RuleSeverity
import com.nurseflow.roster.domain.model.ShiftAssignment
import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.roster.domain.rule.RosterRuleValidator
import com.nurseflow.roster.persistence.entity.DbAbsence
import com.nurseflow.roster.persistence.entity.DbGenerationJob
import com.nurseflow.roster.persistence.entity.DbNurse
import com.nurseflow.roster.persistence.entity.DbRoster
import com.nurseflow.roster.persistence.entity.DbSlot
import com.nurseflow.roster.persistence.entity.NewSlot
import com.nurseflow.roster.persistence.repository.RosterJdbcRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

@Service
class RosterService(
    private val repo: RosterJdbcRepository,
    private val mapper: JsonMapper,
) {
    private val validator = RosterRuleValidator()
    private val diagnosticList = object : TypeReference<List<RuleDiagnostic>>() {}

    fun etag(rosterId: UUID, revision: Int): String = "roster-$rosterId-r$revision"

    fun currentEtag(rosterId: UUID): String {
        val roster = repo.findRoster(rosterId)
            ?: fail(HttpStatus.NOT_FOUND, "ROSTER_NOT_FOUND", "근무표를 찾을 수 없습니다.")
        return etag(roster.id, roster.revision)
    }

    @Transactional(readOnly = true)
    fun listRosters(wardId: UUID, month: String, subject: String): List<RosterSummary> {
        parseMonth(month)
        requireAdmin(wardId, subject)
        return repo.listRosterIds(wardId, month).map { summary(repo.findRoster(it)!!) }
    }

    @Transactional
    fun createRoster(wardId: UUID, request: CreateRosterRequest, subject: String, idempotencyKey: UUID): RosterView {
        parseMonth(request.month)
        requireAdmin(wardId, subject)
        repo.lockWard(wardId)
        val action = "create-roster:$wardId"
        replay(idempotencyKey, action, request, RosterView::class.java)?.let { return it }
        if (repo.draftExistsForMonth(wardId, request.month)) {
            fail(HttpStatus.CONFLICT, "DRAFT_EXISTS", "해당 월의 초안이 이미 있습니다.")
        }
        val publishedAbsences = repo.activePublished(wardId, request.month)?.let { repo.absences(it.id) }.orEmpty()
        val id = UUID.randomUUID()
        repo.insertRoster(id, wardId, request.month)
        publishedAbsences.forEach { absence ->
            repo.insertAbsence(UUID.randomUUID(), id, absence.nurseId, absence.kind, absence.startsAt, absence.endsAt)
        }
        return view(repo.findRoster(id)!!).also { recordReplay(idempotencyKey, action, request, it) }
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
        if (request.items.size > 1000 || request.items.any {
                it.requiredCount !in 0..100 ||
                    it.requiredQualifications.size > 50 || it.requiredQualifications.any { q -> q.length !in 1..64 }
            }) {
            fail(HttpStatus.BAD_REQUEST, "INVALID_REQUIREMENTS", "인력 수요 항목이 허용 범위를 벗어났습니다.")
        }
        if (request.items.map { it.date to it.shiftTypeId }.toSet().size != request.items.size) {
            fail(HttpStatus.BAD_REQUEST, "DUPLICATE_REQUIREMENT", "날짜와 근무 유형 조합이 중복되었습니다.")
        }
        if (request.items.sumOf { it.requiredCount } > 10000) {
            fail(HttpStatus.BAD_REQUEST, "TOO_MANY_SLOTS", "생성할 근무 칸이 너무 많습니다.")
        }
        if (repo.slots(roster.id).any { it.nurseId != null }) {
            fail(HttpStatus.CONFLICT, "ASSIGNMENTS_EXIST", "배정을 해제한 뒤 인력 수요를 변경해 주세요.")
        }
        val month = parseMonth(roster.month)
        val newSlots = request.items.flatMap { item ->
            if (YearMonth.from(item.date) != month) {
                fail(HttpStatus.BAD_REQUEST, "DATE_OUTSIDE_ROSTER", "근무 날짜가 해당 월에 속하지 않습니다.")
            }
            val shift = repo.findShiftDefinition(item.shiftTypeId, roster.wardId)
                ?: fail(HttpStatus.BAD_REQUEST, "SHIFT_TYPE_NOT_FOUND", "근무 유형을 찾을 수 없습니다.")
            val start = item.date.atTime(shift.start).atZone(roster.zone).toInstant()
            val endDate = if (shift.spansMidnight) item.date.plusDays(1) else item.date
            val end = endDate.atTime(shift.end).atZone(roster.zone).toInstant()
            if (!start.isBefore(end)) {
                fail(HttpStatus.UNPROCESSABLE_CONTENT, "INVALID_SHIFT_INTERVAL", "근무 종료 시각은 시작 시각보다 뒤여야 합니다.")
            }
            List(item.requiredCount) {
                NewSlot(item.date, item.shiftTypeId, start, end, item.requiredQualifications.distinct())
            }
        }
        repo.deleteSlots(roster.id)
        newSlots.forEach { repo.insertSlot(roster.id, it) }
        repo.increment(roster.id)
        return summary(repo.findRoster(roster.id)!!)
    }

    @Transactional(readOnly = true)
    fun listAbsences(rosterId: UUID, subject: String): List<ApprovedAbsenceView> {
        visibleToAdmin(rosterId, subject)
        return repo.absences(rosterId).map(::absenceView)
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
        repo.insertAbsence(id, roster.id, input.nurseId, input.kind, input.startsAt, input.endsAt)
        repo.increment(roster.id)
        val result = AbsenceMutation(
            ApprovedAbsenceView(id, input.nurseId, input.kind, input.startsAt, input.endsAt),
            roster.revision + 1,
        )
        recordReplay(idempotencyKey, action, payload, result)
        return result
    }

    @Transactional
    fun updateAbsence(
        rosterId: UUID, absenceId: UUID, ifMatch: String, input: ApprovedAbsenceInput, subject: String,
    ): AbsenceMutation {
        val roster = lockedDraft(rosterId, ifMatch, subject)
        if (repo.absences(roster.id).none { it.id == absenceId }) {
            fail(HttpStatus.NOT_FOUND, "ABSENCE_NOT_FOUND", "부재 일정을 찾을 수 없습니다.")
        }
        validateAbsence(roster, input)
        repo.updateAbsence(absenceId, roster.id, input.nurseId, input.kind, input.startsAt, input.endsAt)
        repo.increment(roster.id)
        return AbsenceMutation(
            ApprovedAbsenceView(absenceId, input.nurseId, input.kind, input.startsAt, input.endsAt),
            roster.revision + 1,
        )
    }

    @Transactional
    fun deleteAbsence(rosterId: UUID, absenceId: UUID, ifMatch: String, subject: String): Int {
        val roster = lockedDraft(rosterId, ifMatch, subject)
        if (!repo.deleteAbsence(absenceId, roster.id)) {
            fail(HttpStatus.NOT_FOUND, "ABSENCE_NOT_FOUND", "부재 일정을 찾을 수 없습니다.")
        }
        repo.increment(roster.id)
        return roster.revision + 1
    }

    @Transactional(readOnly = true)
    fun validate(rosterId: UUID, ifMatch: String, input: ValidateAssignmentRequest, subject: String): AssignmentValidation {
        val roster = visibleToAdmin(rosterId, subject)
        checkRevision(roster, ifMatch)
        if (roster.status != "DRAFT") fail(HttpStatus.CONFLICT, "NOT_DRAFT", "확정된 근무표는 수정할 수 없습니다.")
        val slot = repo.slots(roster.id).firstOrNull { it.id == input.slotId }
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
        val slot = repo.slots(roster.id).firstOrNull { it.id == slotId }
            ?: fail(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "근무 칸을 찾을 수 없습니다.")
        val violations = candidateViolations(roster, slot, input.nurseId)
        if (violations.isNotEmpty()) {
            throw ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, "RULE_VIOLATION", "배치할 수 없는 근무 칸입니다.",
                ruleViolations = violations,
            )
        }
        repo.assignSlot(slotId, roster.id, input.nurseId, input.locked)
        repo.increment(roster.id)
        return view(repo.findRoster(roster.id)!!).also { recordReplay(idempotencyKey, action, payload, it) }
    }

    @Transactional
    fun unassign(rosterId: UUID, slotId: UUID, ifMatch: String, subject: String): Int {
        val roster = lockedDraft(rosterId, ifMatch, subject)
        if (!repo.unassignSlot(slotId, roster.id)) {
            fail(HttpStatus.NOT_FOUND, "SLOT_NOT_FOUND", "근무 칸을 찾을 수 없습니다.")
        }
        repo.increment(roster.id)
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
        val currentSlots = repo.slots(roster.id)
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
            throw ApiException(
                HttpStatus.UNPROCESSABLE_CONTENT, "RULE_VIOLATION",
                "확정 전에 근무 배치를 수정해 주세요.", ruleViolations = violations,
            )
        }
        repo.deactivatePublished(roster.wardId, roster.month)
        repo.publishRoster(roster.id, Instant.now(), subject)
        return summary(repo.findRoster(roster.id)!!).also { recordReplay(idempotencyKey, action, payload, it) }
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
        if (repo.pendingGenerationCount(rosterId) > 0) {
            fail(HttpStatus.TOO_MANY_REQUESTS, "GENERATION_ALREADY_RUNNING", "자동배치가 이미 진행 중입니다.")
        }
        val all = repo.slots(rosterId)
        if (all.size > 2000) fail(HttpStatus.UNPROCESSABLE_CONTENT, "GENERATION_TOO_LARGE", "자동배치 한도는 2000칸입니다.")
        if (repo.activeNurseCount(roster.wardId) > 200) {
            fail(HttpStatus.UNPROCESSABLE_CONTENT, "GENERATION_TOO_LARGE", "자동배치 한도는 활성 간호사 200명입니다.")
        }
        val job = GenerationJob(UUID.randomUUID(), rosterId, "QUEUED", roster.revision, Instant.now(), progressPercent = 0)
        repo.insertGenerationJob(
            job.jobId, rosterId, subject, roster.revision,
            request.preserveLockedAssignments, request.timeLimitSeconds, job.createdAt,
        )
        recordReplay(idempotencyKey, action, payload, job)
        return job
    }

    @Transactional(readOnly = true)
    fun getGenerationJob(jobId: UUID, subject: String): GenerationJob {
        val rosterId = repo.jobRosterId(jobId)
            ?: fail(HttpStatus.NOT_FOUND, "GENERATION_JOB_NOT_FOUND", "자동배치 작업을 찾을 수 없습니다.")
        visibleToAdmin(rosterId, subject)
        return readGenerationJob(jobId)
            ?: fail(HttpStatus.NOT_FOUND, "GENERATION_JOB_NOT_FOUND", "자동배치 작업을 찾을 수 없습니다.")
    }

    @Transactional
    fun cancelGeneration(jobId: UUID, idempotencyKey: UUID, subject: String): GenerationJob {
        val rosterId = repo.jobRosterId(jobId)
            ?: fail(HttpStatus.NOT_FOUND, "GENERATION_JOB_NOT_FOUND", "자동배치 작업을 찾을 수 없습니다.")
        visibleToAdmin(rosterId, subject)
        val action = "cancel-generation:$jobId"
        replay(idempotencyKey, action, jobId, GenerationJob::class.java)?.let { return it }
        repo.lockGenerationJob(jobId)
        val job = readGenerationJob(jobId)
            ?: fail(HttpStatus.NOT_FOUND, "GENERATION_JOB_NOT_FOUND", "자동배치 작업을 찾을 수 없습니다.")
        if (job.status !in setOf("QUEUED", "RUNNING")) {
            fail(HttpStatus.CONFLICT, "GENERATION_NOT_CANCELLABLE", "이미 종료된 자동배치 작업입니다.")
        }
        if (job.status == "QUEUED") repo.cancelQueuedJob(jobId) else repo.requestCancel(jobId)
        val result = readGenerationJob(jobId)!!
        recordReplay(idempotencyKey, action, jobId, result)
        return result
    }

    @Transactional
    fun markGenerationRunning(jobId: UUID): Boolean = repo.markRunning(jobId)

    @Transactional
    fun failGeneration(jobId: UUID) = repo.markFailed(jobId)

    @Transactional
    fun processGeneration(jobId: UUID) {
        val initial = readGenerationJob(jobId) ?: return
        if (initial.status != "RUNNING") return
        val requestedBy = repo.generationRequestedBy(jobId)
        val roster = lockAuthorizedRoster(initial.rosterId, requestedBy)
        if (roster.status != "DRAFT" || roster.revision != initial.sourceRosterRevision) {
            finishGeneration(
                jobId, "FAILED", null, null,
                listOf(RuleDiagnostic("STALE_ROSTER_REVISION", "HARD", "근무표가 변경되어 자동배치를 적용하지 않았습니다.")),
            )
            return
        }
        val allSlots = repo.slots(roster.id)
        val preserveLocked = repo.preserveLocked(jobId)
        val preserved = allSlots.filter { it.nurseId != null && it.locked && preserveLocked }
        val removedIds = allSlots.filter { it.nurseId != null && it !in preserved }.map { it.id }.toSet()
        val context = loadValidationContext(roster, allSlots)
        val assignments = context.assignments.filterNot { it.assignmentId in removedIds }.toMutableList()
        val nurses = repo.activeNurses(roster.wardId)
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
        val deadline = System.nanoTime() + repo.timeLimitNanos(jobId)
        var timedOut = false
        var cancelled = false
        generationLoop@ for (slot in allSlots.filter { it !in preserved }.sortedWith(
            compareBy<DbSlot>({ it.date }, { it.startsAt }, { it.id.toString() }),
        )) {
            if (repo.generationCancelled(jobId)) { cancelled = true; break }
            if (System.nanoTime() >= deadline) { timedOut = true; break }
            val ordered = nurses.sortedWith(
                compareBy<DbNurse>(
                    { counts[it.id] ?: 0 },
                    { nightCounts[it.id] ?: 0 },
                    { it.id.toString() },
                ),
            )
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
        if (cancelled || repo.generationCancelled(jobId)) {
            finishGeneration(jobId, "CANCELLED", null, null, emptyList())
            return
        }
        if (preserveLocked) repo.clearUnlockedAssignments(roster.id) else repo.clearAllAssignments(roster.id)
        selected.forEach { (slotId, nurseId) -> repo.assignSlot(slotId, roster.id, nurseId, false) }
        val finalSlots = repo.slots(roster.id)
        val finalContext = loadValidationContext(roster, finalSlots)
        val nurseCache = mutableMapOf<UUID, DbNurse?>()
        val finalViolations = finalSlots.filter { it.nurseId != null }.flatMap { slot ->
            candidateViolations(roster, slot, slot.nurseId!!, finalContext, nurseCache)
        } + draftAbsenceConflicts(roster)
        if (finalViolations.isNotEmpty()) {
            throw IllegalStateException("Automatic roster assignment failed final hard-rule validation")
        }
        repo.increment(roster.id)
        val staffing = StaffingSummary(
            finalSlots.size, finalSlots.count { it.nurseId != null }, finalSlots.count { it.nurseId == null },
        )
        val diagnostics = finalSlots.filter { it.nurseId == null }.map {
            RuleDiagnostic("UNFILLED_SLOT", "SOFT", "현재 배치 순서와 필수 규칙으로 채우지 못한 칸입니다.", it.date)
        }.distinctBy { it.code to it.affectedDate } + if (timedOut) {
            listOf(RuleDiagnostic("TIME_LIMIT_REACHED", "SOFT", "제한 시간에 도달해 남은 칸은 검토가 필요합니다."))
        } else {
            emptyList()
        }
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
        val roster = repo.activePublished(wardId, month)
            ?: fail(HttpStatus.NOT_FOUND, "PUBLISHED_ROSTER_NOT_FOUND", "공개된 근무표가 없습니다.")
        val v = view(roster)
        return PublishedWardCalendar(wardId, month, roster.revision, roster.zone.id, v.staffing, v.slots)
    }

    @Transactional(readOnly = true)
    fun publishedMine(wardId: UUID, month: String, subject: String): PublishedPersonalCalendar {
        parseMonth(month)
        requireMember(wardId, subject)
        val nurseId = repo.findNurseIdBySubject(wardId, subject)
            ?: fail(HttpStatus.FORBIDDEN, "NURSE_IDENTITY_REQUIRED", "본인 근무표를 조회할 수 없습니다.")
        val roster = repo.activePublished(wardId, month)
            ?: fail(HttpStatus.NOT_FOUND, "PUBLISHED_ROSTER_NOT_FOUND", "공개된 근무표가 없습니다.")
        val own = repo.slots(roster.id).filter { it.nurseId == nurseId }
            .map { PersonalAssignment(it.date, it.shiftCode, it.startsAt, it.endsAt) }
        return PublishedPersonalCalendar(wardId, month, roster.revision, roster.zone.id, own)
    }

    // ── Internal helpers ──

    private fun candidateViolations(
        roster: DbRoster,
        slot: DbSlot,
        nurseId: UUID,
        context: ValidationContext = loadValidationContext(roster),
        nursesById: MutableMap<UUID, DbNurse?> = mutableMapOf(),
    ): List<RuleDiagnostic> {
        val nurse = nursesById.getOrPut(nurseId) { repo.findNurse(nurseId, roster.wardId) }
            ?: fail(HttpStatus.NOT_FOUND, "NURSE_NOT_FOUND", "병동 간호사를 찾을 수 없습니다.")
        val extra = mutableListOf<RuleDiagnostic>()
        if (!nurse.active) extra += RuleDiagnostic("NURSE_INACTIVE", "HARD", "활성화되지 않은 간호사입니다.", slot.date)
        if (!nurse.qualifications.containsAll(slot.requiredQualifications)) {
            extra += RuleDiagnostic("QUALIFICATION_MISSING", "HARD", "필수 자격이 부족합니다.", slot.date)
        }
        val candidate = ShiftAssignment(slot.id, nurseId, slot.date, slot.kind, slot.startsAt, slot.endsAt)
        return extra + validator.validate(candidate, context.assignments, context.absences).map(RuleDiagnostic::from)
    }

    private fun loadValidationContext(roster: DbRoster, ownSlots: List<DbSlot> = repo.slots(roster.id)): ValidationContext {
        val adjacentPublished = repo.adjacentPublishedRosterIds(roster.wardId, roster.month).flatMap { repo.slots(it) }
        val existing = (ownSlots + adjacentPublished).mapNotNull { it.assignment() }
        val adjacentAbsences = repo.adjacentPublishedAbsences(roster.wardId, roster.month)
        val approved = (repo.absences(roster.id) + adjacentAbsences).map {
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
        if (repo.findNurse(input.nurseId, roster.wardId) == null) {
            fail(HttpStatus.NOT_FOUND, "NURSE_NOT_FOUND", "병동 간호사를 찾을 수 없습니다.")
        }
        val adjacentPublished = repo.adjacentPublishedRosterIds(roster.wardId, roster.month).flatMap { repo.slots(it) }
        val conflict = (repo.slots(roster.id) + adjacentPublished).any { slot ->
            slot.nurseId == input.nurseId && input.startsAt < slot.endsAt && slot.startsAt < input.endsAt
        }
        if (conflict) {
            fail(HttpStatus.UNPROCESSABLE_CONTENT, "ABSENCE_ASSIGNMENT_OVERLAP", "기존 배정과 부재 시간이 겹칩니다.")
        }
    }

    private fun draftAbsenceConflicts(roster: DbRoster): List<RuleDiagnostic> {
        val adjacentPublished = repo.adjacentPublishedRosterIds(roster.wardId, roster.month).flatMap { repo.slots(it) }
        return repo.absences(roster.id).flatMap { absence ->
            adjacentPublished.filter { slot ->
                slot.nurseId == absence.nurseId && absence.startsAt < slot.endsAt && slot.startsAt < absence.endsAt
            }.map { slot ->
                RuleDiagnostic(
                    "APPROVED_ABSENCE_PUBLISHED_OVERLAP", "HARD",
                    "승인된 부재 시간과 다른 월의 공개 근무가 겹칩니다.", slot.date,
                )
            }
        }
    }

    private fun view(roster: DbRoster): RosterView {
        val all = repo.slots(roster.id)
        return RosterView(
            roster.id, roster.wardId, roster.month, roster.status, roster.revision, roster.zone.id,
            StaffingSummary(all.size, all.count { it.nurseId != null }, all.count { it.nurseId == null }),
            all.map {
                SlotView(
                    it.id, it.date, it.shiftTypeId, it.startsAt, it.endsAt,
                    if (it.nurseId == null) "UNFILLED" else "FILLED",
                    it.nurseId?.let { nurseId -> AssignmentView(nurseId, it.nurseName ?: "", it.locked) },
                )
            },
            roster.publishedAt,
        )
    }

    private fun summary(roster: DbRoster): RosterSummary {
        val all = repo.slots(roster.id)
        return RosterSummary(
            roster.id, roster.wardId, roster.month, roster.status, roster.revision, roster.zone.id,
            StaffingSummary(all.size, all.count { it.nurseId != null }, all.count { it.nurseId == null }),
            roster.publishedAt,
        )
    }

    private fun visibleToAdmin(id: UUID, subject: String): DbRoster {
        val roster = repo.findRoster(id)
            ?: fail(HttpStatus.NOT_FOUND, "ROSTER_NOT_FOUND", "근무표를 찾을 수 없습니다.")
        requireAdmin(roster.wardId, subject)
        return roster
    }

    private fun lockAuthorizedRoster(id: UUID, subject: String): DbRoster {
        val roster = visibleToAdmin(id, subject)
        repo.lockWard(roster.wardId)
        repo.lockRoster(id)
        return repo.findRoster(id)!!
    }

    private fun lockedDraft(id: UUID, ifMatch: String, subject: String): DbRoster =
        lockAuthorizedRoster(id, subject).also {
            checkRevision(it, ifMatch)
            ensureDraft(it)
        }

    private fun ensureDraft(roster: DbRoster) {
        if (roster.status != "DRAFT") fail(HttpStatus.CONFLICT, "NOT_DRAFT", "확정된 근무표는 수정할 수 없습니다.")
    }

    private fun absenceView(absence: DbAbsence) = ApprovedAbsenceView(
        absence.id, absence.nurseId, absence.kind, absence.startsAt, absence.endsAt,
    )

    private fun readGenerationJob(jobId: UUID): GenerationJob? = repo.readGenerationJob(jobId)?.let(::generationJobView)

    private fun generationJobView(job: DbGenerationJob) = GenerationJob(
        jobId = job.jobId,
        rosterId = job.rosterId,
        status = job.status,
        sourceRosterRevision = job.sourceRevision,
        createdAt = job.createdAt,
        progressPercent = when (job.status) {
            "QUEUED" -> 0
            "RUNNING" -> null
            else -> 100
        },
        resultRosterRevision = job.resultRevision,
        staffing = job.staffingJson?.let { mapper.readValue(it, StaffingSummary::class.java) },
        diagnostics = job.diagnosticsJson?.let { mapper.readValue(it, diagnosticList) } ?: emptyList(),
        finishedAt = job.finishedAt,
    )

    private fun finishGeneration(
        jobId: UUID, status: String, resultRevision: Int?,
        staffing: StaffingSummary?, diagnostics: List<RuleDiagnostic>,
    ) = repo.finishGeneration(
        jobId, status, resultRevision,
        staffing?.let(mapper::writeValueAsString), mapper.writeValueAsString(diagnostics),
    )

    private fun <T> replay(key: UUID, action: String, payload: Any, type: Class<T>): T? =
        repo.replay(key, action, requestHash(payload), type)

    private fun recordReplay(key: UUID, action: String, payload: Any, response: Any) =
        repo.recordReplay(key, action, requestHash(payload), response)

    private fun requestHash(payload: Any): String = MessageDigest.getInstance("SHA-256")
        .digest(mapper.writeValueAsBytes(payload))
        .joinToString("") { "%02x".format(it) }

    private fun checkRevision(roster: DbRoster, ifMatch: String) {
        if (ifMatch != "\"${etag(roster.id, roster.revision)}\"") {
            throw ApiException(
                HttpStatus.PRECONDITION_FAILED, "STALE_ROSTER_REVISION",
                "근무표가 변경되었습니다. 다시 불러와 주세요.", currentRevision = roster.revision,
            )
        }
    }

    private fun requireAdmin(wardId: UUID, subject: String) {
        val roles = repo.memberships(wardId, subject)
        if (roles.isEmpty()) fail(HttpStatus.NOT_FOUND, "WARD_NOT_FOUND", "병동을 찾을 수 없습니다.")
        if ("WARD_ADMIN" !in roles) fail(HttpStatus.FORBIDDEN, "WARD_ADMIN_REQUIRED", "관리자 권한이 필요합니다.")
    }

    private fun requireMember(wardId: UUID, subject: String) {
        if (repo.memberships(wardId, subject).isEmpty()) {
            fail(HttpStatus.NOT_FOUND, "WARD_NOT_FOUND", "병동을 찾을 수 없습니다.")
        }
    }

    private fun requireWardView(wardId: UUID, subject: String) {
        requireMember(wardId, subject)
        if (!repo.canViewWardCalendar(wardId, subject)) {
            fail(HttpStatus.FORBIDDEN, "WARD_CALENDAR_VIEW_REQUIRED", "병동 전체 근무표 조회 권한이 없습니다.")
        }
    }

    private fun parseMonth(value: String): YearMonth = try {
        if (!Regex("^\\d{4}-(0[1-9]|1[0-2])$").matches(value)) throw IllegalArgumentException()
        YearMonth.parse(value)
    } catch (_: Exception) {
        fail(HttpStatus.BAD_REQUEST, "INVALID_MONTH", "월은 YYYY-MM 형식이어야 합니다.")
    }

    private fun fail(status: HttpStatus, code: String, detail: String): Nothing =
        throw ApiException(status, code, detail)

    private data class ValidationContext(
        val assignments: List<ShiftAssignment>,
        val absences: List<ApprovedAbsence>,
    )
}
