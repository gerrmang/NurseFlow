package com.nurseflow.ward.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.common.idempotency.MutationReplayRepository
import com.nurseflow.common.idempotency.ReplayLookup
import com.nurseflow.hospital.application.service.HospitalSearchService
import com.nurseflow.ward.application.model.CreateWardCommand
import com.nurseflow.ward.application.model.WardMembershipResult
import com.nurseflow.ward.application.model.WardRole
import com.nurseflow.ward.config.WardProperties
import com.nurseflow.ward.persistence.repository.NewWard
import com.nurseflow.ward.persistence.repository.WardJdbcRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.DateTimeException
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

@Service
class WardService(
    private val repo: WardJdbcRepository,
    private val hospitals: HospitalSearchService,
    private val audit: AuditLog,
    private val transactions: TransactionTemplate,
    private val replays: MutationReplayRepository,
    private val properties: WardProperties,
) {
    /** Only memberships stored on the server count; token claims never grant ward access. */
    @Transactional(readOnly = true)
    fun membershipsOf(subject: String): List<WardMembershipResult> = repo.membershipsOf(subject)
        .groupBy { it.wardId }
        .map { (wardId, rows) ->
            WardMembershipResult(
                wardId = wardId,
                wardName = rows.first().wardName,
                roles = rows.map { WardRole.valueOf(it.role) }.distinct().sorted(),
                canViewWardCalendar = rows.any { it.canViewWardCalendar },
                hospitalName = rows.first().hospitalName,
            )
        }

    @Transactional(readOnly = true)
    fun requireAdmin(wardId: UUID, subject: String) {
        val roles = repo.rolesIn(wardId, subject)
        if (roles.isEmpty()) throw BusinessException(ErrorCode.WARD_NOT_FOUND)
        if (WardRole.WARD_ADMIN.name !in roles) throw BusinessException(ErrorCode.WARD_ADMIN_REQUIRED)
    }

    /**
     * Entry point of every admin write: checks the role, takes the ward lock, then checks again. A membership
     * change committed while this request waited for the lock (A6) must not let a revoked or deactivated admin
     * finish. Lock order stays ward -> account -> target row.
     */
    @Transactional
    fun lockAsAdmin(wardId: UUID, subject: String) {
        requireAdmin(wardId, subject)
        if (!repo.lockWard(wardId)) throw BusinessException(ErrorCode.WARD_NOT_FOUND)
        requireAdmin(wardId, subject)
    }

    @Transactional(readOnly = true)
    fun requireMember(wardId: UUID, subject: String) {
        if (repo.rolesIn(wardId, subject).isEmpty()) throw BusinessException(ErrorCode.WARD_NOT_FOUND)
    }

    /**
     * Creates a ward for a hospital from the official list and makes the caller its only WARD_ADMIN.
     * No nurse record is created. Picking a hospital proves no employment and grants nothing on other
     * wards of the same hospital, so the same hospital and ward name may be used by several admins.
     * Retrying with the same Idempotency-Key returns the first result; a concurrent duplicate loses on
     * the replay row's primary key and is answered from it.
     */
    fun createWard(request: CreateWardCommand, idempotencyKey: UUID, subject: String): WardMembershipResult {
        // Hash what is stored, so a retry that differs only in surrounding spaces replays instead of conflicting.
        val command = request.copy(
            hospitalExternalId = request.hospitalExternalId.trim(),
            wardName = request.wardName.trim(),
            timeZone = request.timeZone.trim(),
        )
        val requestHash = replays.requestHash(listOf(subject, command))
        replayed(idempotencyKey, requestHash)?.let { return it }
        return try {
            transactions.execute { create(command, idempotencyKey, requestHash, subject) }!!
        } catch (ex: DuplicateKeyException) {
            replayed(idempotencyKey, requestHash) ?: throw ex
        }
    }

    private fun create(command: CreateWardCommand, key: UUID, requestHash: String, subject: String): WardMembershipResult {
        val zone = try {
            ZoneId.of(command.timeZone)
        } catch (_: DateTimeException) {
            throw BusinessException(ErrorCode.INVALID_TIME_ZONE)
        }
        val hospital = hospitals.requireForWard(command.hospitalSource, command.hospitalExternalId)
        val userId = runCatching { UUID.fromString(subject) }.getOrNull()
        if (userId == null || !repo.lockUser(userId)) throw BusinessException(ErrorCode.USER_NOT_FOUND)
        // A same-key request that waited on this lock sees the committed first result here and replays it,
        // instead of failing the duplicate or cap check below.
        replayed(key, requestHash)?.let { return it }
        // A second ward with the same hospital and name by the same account is almost always a double submit
        // with a fresh key; other admins may still use the same names (decision 3).
        if (repo.existsCreatedBy(subject, hospital.source, hospital.externalId, command.wardName)) {
            throw BusinessException(ErrorCode.WARD_ALREADY_CREATED)
        }
        if (repo.countCreatedBy(subject) >= properties.maxCreatedPerUser) {
            throw BusinessException(ErrorCode.WARD_CREATION_LIMIT_REACHED)
        }
        val ward = NewWard(
            wardId = UUID.randomUUID(),
            name = command.wardName,
            timeZone = zone.id,
            hospitalSource = hospital.source,
            hospitalExternalId = hospital.externalId,
            hospitalName = hospital.name,
            hospitalAddress = hospital.address,
            createdBy = subject,
            createdAt = Instant.now(),
        )
        repo.insertWard(ward)
        repo.insertMembership(ward.wardId, subject, WardRole.WARD_ADMIN.name, canViewWardCalendar = true)
        audit.record(ward.wardId, subject, "WARD_CREATED", hospital.externalId)
        val result = WardMembershipResult(ward.wardId, ward.name, listOf(WardRole.WARD_ADMIN), true, hospital.name)
        replays.recordReplay(key, OPERATION, requestHash, result)
        return result
    }

    /** The key is global, so another user's key or a different body is a conflict rather than a replay. */
    private fun replayed(key: UUID, requestHash: String): WardMembershipResult? =
        when (val found = replays.replay(key, OPERATION, requestHash, WardMembershipResult::class.java)) {
            ReplayLookup.Missing -> null
            ReplayLookup.Conflict -> throw BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED)
            is ReplayLookup.Found -> found.value
        }

    private companion object {
        const val OPERATION = "CREATE_WARD"
    }
}
