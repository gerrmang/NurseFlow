package com.nurseflow.policy.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.idempotency.MutationReplayRepository
import com.nurseflow.common.idempotency.ReplayLookup
import com.nurseflow.policy.application.model.ShiftTypeCommand
import com.nurseflow.policy.application.model.ShiftTypeListResult
import com.nurseflow.policy.application.model.ShiftTypeResult
import com.nurseflow.policy.application.model.ShiftTypeRow
import com.nurseflow.policy.persistence.repository.ShiftTypeJdbcRepository
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
 * runs under the ward lock, bumps the ward policy revision and flags DRAFT rosters that use the type.
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

    /** Retrying with the same Idempotency-Key returns the first result instead of a duplicate-code 409. */
    @Transactional
    fun create(wardId: UUID, command: ShiftTypeCommand, idempotencyKey: UUID, actor: String): ShiftTypeResult {
        wards.requireAdmin(wardId, actor)
        val resolved = resolve(command)
        val requestHash = replays.requestHash(listOf(actor, wardId, command))
        replayed(idempotencyKey, requestHash)?.let { return it }
        lock(wardId)
        // A same-key request that waited on the lock sees the committed first result here.
        replayed(idempotencyKey, requestHash)?.let { return it }
        if (repo.codeExists(wardId, command.code, except = null)) throw BusinessException(ErrorCode.SHIFT_TYPE_CODE_DUPLICATE)
        val row = resolved.toRow(UUID.randomUUID(), wardId, command.active ?: true)
        repo.insert(row)
        repo.bumpPolicyRevision(wardId, actor, Instant.now())
        audit.record(wardId, actor, "SHIFT_TYPE_CREATED", row.id.toString())
        return row.toResult().also { replays.recordReplay(idempotencyKey, CREATE_OPERATION, requestHash, it) }
    }

    @Transactional
    fun update(wardId: UUID, shiftTypeId: UUID, command: ShiftTypeCommand, actor: String): ShiftTypeResult {
        wards.requireAdmin(wardId, actor)
        val resolved = resolve(command)
        lock(wardId)
        val old = repo.find(wardId, shiftTypeId) ?: throw BusinessException(ErrorCode.SHIFT_TYPE_NOT_FOUND_IN_WARD)
        if (repo.codeExists(wardId, command.code, except = shiftTypeId)) throw BusinessException(ErrorCode.SHIFT_TYPE_CODE_DUPLICATE)
        val row = resolved.toRow(shiftTypeId, wardId, command.active ?: old.active)
        repo.update(row)
        repo.bumpPolicyRevision(wardId, actor, Instant.now())
        if (old.start != row.start || old.end != row.end || old.spansMidnight != row.spansMidnight) {
            repo.recomputeDraftSlotTimes(wardId, shiftTypeId, row.start, row.end, row.spansMidnight)
        }
        if (affectsSlots(old.toResult(), row.toResult())) repo.markDraftsForRevalidation(wardId, shiftTypeId)
        audit.record(
            wardId, actor,
            if (old.active && !row.active) "SHIFT_TYPE_DEACTIVATED" else "SHIFT_TYPE_UPDATED", shiftTypeId.toString(),
        )
        return row.toResult()
    }

    @Transactional
    fun deactivate(wardId: UUID, shiftTypeId: UUID, actor: String): ShiftTypeResult {
        wards.requireAdmin(wardId, actor)
        lock(wardId)
        val old = repo.find(wardId, shiftTypeId) ?: throw BusinessException(ErrorCode.SHIFT_TYPE_NOT_FOUND_IN_WARD)
        if (!old.active) return old.toResult()
        val row = old.copy(active = false)
        repo.update(row)
        repo.bumpPolicyRevision(wardId, actor, Instant.now())
        repo.markDraftsForRevalidation(wardId, shiftTypeId)
        audit.record(wardId, actor, "SHIFT_TYPE_DEACTIVATED", shiftTypeId.toString())
        return row.toResult()
    }

    private fun lock(wardId: UUID) {
        if (!repo.lockWard(wardId)) throw BusinessException(ErrorCode.WARD_NOT_FOUND)
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

        fun durationMinutes(start: LocalTime, end: LocalTime, spansMidnight: Boolean): Int =
            Duration.between(start, end).toMinutes().toInt() + if (spansMidnight) 24 * 60 else 0
    }
}
