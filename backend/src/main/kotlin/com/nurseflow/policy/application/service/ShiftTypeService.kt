package com.nurseflow.policy.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.idempotency.MutationReplayRepository
import com.nurseflow.common.idempotency.ReplayLookup
import com.nurseflow.policy.application.model.AffectedDraft
import com.nurseflow.policy.application.model.ShiftTypeChangePreview
import com.nurseflow.policy.application.model.ShiftTypeCommand
import com.nurseflow.policy.application.model.ShiftTypeListResult
import com.nurseflow.policy.application.model.ShiftTypeResult
import com.nurseflow.policy.application.model.ShiftTypeWrite
import com.nurseflow.policy.application.model.ShiftTypeRow
import com.nurseflow.policy.persistence.repository.ShiftTypeJdbcRepository
import com.nurseflow.policy.persistence.repository.SlotTimes
import com.nurseflow.roster.domain.model.ShiftKind
import com.nurseflow.ward.application.service.WardService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

/**
 * Ward shift type settings (D/E/N hours, break, recognized hours, monthly target credit). Every write
 * runs under the ward lock with the admin role re-checked after it ([WardService.lockAsAdmin]), bumps the ward
 * policy revision and flags DRAFT rosters that use the type.
 * Shift types are never deleted because roster slots reference them.
 */
@Service
class ShiftTypeService(
    private val repo: ShiftTypeJdbcRepository,
    private val wards: WardService,
    private val audit: AuditLog,
    private val replays: MutationReplayRepository,
) {
    @Transactional(readOnly = true)
    fun list(wardId: UUID, includeInactive: Boolean, actor: String): ShiftTypeListResult {
        wards.requireMember(wardId, actor)
        return ShiftTypeListResult(
            repo.list(wardId, includeInactive).map { it.toResult() },
            repo.policyRevision(wardId),
        )
    }

    /**
     * Retrying with the same Idempotency-Key returns the first result instead of a duplicate-code 409. The replay
     * is read only after the lock and admin re-check, so a revoked admin gets no replay either, and a same-key
     * request that waited on the lock sees the committed first result.
     */
    @Transactional
    fun create(wardId: UUID, command: ShiftTypeCommand, idempotencyKey: UUID, actor: String): ShiftTypeWrite {
        wards.lockAsAdmin(wardId, actor)
        val resolved = resolve(command)
        val requestHash = replays.requestHash(listOf(actor, wardId, command))
        replayed(idempotencyKey, requestHash)?.let { return written(wardId, it) }
        if (repo.codeExists(wardId, command.code, except = null)) throw BusinessException(ErrorCode.SHIFT_TYPE_CODE_DUPLICATE)
        val row = resolved.toRow(UUID.randomUUID(), wardId, command.active ?: true)
        repo.insert(row)
        repo.bumpPolicyRevision(wardId, actor, Instant.now())
        audit.record(wardId, actor, "SHIFT_TYPE_CREATED", row.id.toString())
        return written(wardId, row.toResult().also { replays.recordReplay(idempotencyKey, CREATE_OPERATION, requestHash, it) })
    }

    /**
     * Full replacement. [ifMatch] is the [policyEtag] the caller read: checked under the ward lock, so of two admins
     * editing from the same policy revision the second gets 412 instead of silently overwriting the first.
     */
    @Transactional
    fun update(wardId: UUID, shiftTypeId: UUID, command: ShiftTypeCommand, ifMatch: String, actor: String): ShiftTypeWrite {
        wards.lockAsAdmin(wardId, actor)
        val resolved = resolve(command)
        val old = repo.find(wardId, shiftTypeId) ?: throw BusinessException(ErrorCode.SHIFT_TYPE_NOT_FOUND_IN_WARD)
        checkPolicyRevision(wardId, ifMatch)
        val change = plan(wardId, old, resolved.toRow(shiftTypeId, wardId, command.active ?: old.active))
        val row = change.row
        repo.update(row)
        repo.bumpPolicyRevision(wardId, actor, Instant.now())
        change.newSlotTimes?.let(repo::rewriteDraftSlotTimes)
        if (old.code != row.code || old.kind != row.kind) repo.syncDraftSlotCodeAndKind(wardId, shiftTypeId, row.code, row.kind)
        if (change.flagsDrafts) repo.markDraftsForRevalidation(wardId, shiftTypeId)
        audit.record(
            wardId, actor,
            if (old.active && !row.active) "SHIFT_TYPE_DEACTIVATED" else "SHIFT_TYPE_UPDATED", shiftTypeId.toString(),
        )
        return written(wardId, row.toResult())
    }

    /** Already inactive is a no-op without a revision check: nothing is written, so nothing can be overwritten. */
    @Transactional
    fun deactivate(wardId: UUID, shiftTypeId: UUID, ifMatch: String, actor: String): ShiftTypeWrite {
        wards.lockAsAdmin(wardId, actor)
        val old = repo.find(wardId, shiftTypeId) ?: throw BusinessException(ErrorCode.SHIFT_TYPE_NOT_FOUND_IN_WARD)
        if (!old.active) return written(wardId, old.toResult())
        checkPolicyRevision(wardId, ifMatch)
        val row = old.copy(active = false)
        repo.update(row)
        repo.bumpPolicyRevision(wardId, actor, Instant.now())
        repo.markDraftsForRevalidation(wardId, shiftTypeId)
        audit.record(wardId, actor, "SHIFT_TYPE_DEACTIVATED", shiftTypeId.toString())
        return written(wardId, row.toResult())
    }

    /**
     * What saving [command] over the type would do, without writing: the same validation and errors as [update]
     * (including the daylight-saving check of draft slots), the DRAFT rosters it would flag and the published
     * rosters that keep their values. A later save still checks the policy revision the preview reports.
     */
    @Transactional(readOnly = true)
    fun preview(wardId: UUID, shiftTypeId: UUID, command: ShiftTypeCommand, actor: String): ShiftTypeChangePreview {
        wards.requireAdmin(wardId, actor)
        val resolved = resolve(command)
        val old = repo.find(wardId, shiftTypeId) ?: throw BusinessException(ErrorCode.SHIFT_TYPE_NOT_FOUND_IN_WARD)
        val change = plan(wardId, old, resolved.toRow(shiftTypeId, wardId, command.active ?: old.active))
        return ShiftTypeChangePreview(
            shiftType = change.row.toResult(),
            policyRevision = repo.policyRevision(wardId),
            flagsDrafts = change.flagsDrafts,
            rewritesDraftSlotTimes = change.newSlotTimes != null,
            affectedDrafts = if (change.flagsDrafts) repo.draftUsage(wardId, shiftTypeId).map {
                AffectedDraft(it.rosterId, it.month, it.revision, it.slots, it.assigned)
            } else emptyList(),
            publishedRostersUsingType = repo.publishedRosterCount(wardId, shiftTypeId),
        )
    }

    private class Change(val row: ShiftTypeRow, val flagsDrafts: Boolean, val newSlotTimes: List<SlotTimes>?)

    /** Validation and effects shared by [update] and [preview], so a preview never promises what a save refuses. */
    private fun plan(wardId: UUID, old: ShiftTypeRow, row: ShiftTypeRow): Change {
        if (repo.codeExists(wardId, row.code, except = row.id)) throw BusinessException(ErrorCode.SHIFT_TYPE_CODE_DUPLICATE)
        val hoursChanged = old.start != row.start || old.end != row.end || old.spansMidnight != row.spansMidnight
        val newSlotTimes = if (!hoursChanged) null else {
            repo.draftSlotTimes(wardId, row.id, row.start, row.end, row.spansMidnight) ?: throw BusinessException(
                ErrorCode.INVALID_SHIFT_INTERVAL,
                safeDetail = "병동 시간대의 일광절약시간 전환일에 종료가 시작보다 앞서는 초안 근무 칸이 생깁니다.",
            )
        }
        return Change(row, affectsSlots(old.toResult(), row.toResult()), newSlotTimes)
    }

    /** Read while the ward lock is held, so it is the revision this write produced. */
    private fun written(wardId: UUID, result: ShiftTypeResult) = ShiftTypeWrite(result, repo.policyRevision(wardId))

    private fun checkPolicyRevision(wardId: UUID, ifMatch: String) {
        val current = repo.policyRevision(wardId)
        if (ifMatch != policyEtag(current)) {
            throw BusinessException(ErrorCode.STALE_POLICY_REVISION, currentRevision = current)
        }
    }

    /** Fields that change how existing slots of the type should look; name and code alone do not. */
    private fun affectsSlots(old: ShiftTypeResult, new: ShiftTypeResult) =
        old.copy(code = "", displayName = "") != new.copy(code = "", displayName = "")

    private class Resolved(val command: ShiftTypeCommand, val breakMinutes: Int, val recognized: Int, val credit: Int) {
        fun toRow(id: UUID, wardId: UUID, active: Boolean) = ShiftTypeRow(
            id, wardId, command.code, command.displayName.trim(), command.kind, command.start, command.end,
            command.spansMidnight, active, breakMinutes, recognized, credit,
        )
    }

    private fun replayed(key: UUID, requestHash: String): ShiftTypeResult? =
        when (val found = replays.replay(key, CREATE_OPERATION, requestHash, ShiftTypeResult::class.java)) {
            ReplayLookup.Missing -> null
            ReplayLookup.Conflict -> throw BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED)
            is ReplayLookup.Found -> found.value
        }

    private fun resolve(command: ShiftTypeCommand): Resolved {
        // OTHER has no rest rules yet (decision 23 pending); allowing it would let a night-like type skip them.
        if (command.kind == ShiftKind.OTHER) throw BusinessException(ErrorCode.INVALID_REQUEST)
        if (command.start == command.end) throw BusinessException(ErrorCode.INVALID_SHIFT_INTERVAL)
        if (command.spansMidnight != command.end.isBefore(command.start)) {
            throw BusinessException(ErrorCode.SHIFT_SPANS_MIDNIGHT_MISMATCH)
        }
        val duration = durationMinutes(command.start, command.end, command.spansMidnight)
        val breakMinutes = command.breakMinutes ?: 0
        val recognized = command.recognizedMinutes ?: (duration - breakMinutes)
        if (recognized !in 1..(duration - breakMinutes)) throw BusinessException(ErrorCode.INVALID_SHIFT_MINUTES)
        return Resolved(command, breakMinutes, recognized, command.targetCreditMinutes ?: recognized)
    }

    private fun ShiftTypeRow.toResult(): ShiftTypeResult {
        val duration = durationMinutes(start, end, spansMidnight)
        val recognized = recognizedMinutes ?: (duration - breakMinutes).coerceAtLeast(1)
        return ShiftTypeResult(
            id, code, displayName, kind, start, end, spansMidnight, duration, breakMinutes, recognized,
            targetCreditMinutes ?: recognized, active,
        )
    }

    companion object {
        private const val CREATE_OPERATION = "CREATE_SHIFT_TYPE"

        /** ETag of a ward's shift type policy: list and write responses carry it, and PUT/DELETE require it in If-Match. */
        fun policyEtag(revision: Int) = "\"policy-$revision\""

        fun durationMinutes(start: LocalTime, end: LocalTime, spansMidnight: Boolean): Int =
            Duration.between(start, end).toMinutes().toInt() + if (spansMidnight) 24 * 60 else 0
    }
}
