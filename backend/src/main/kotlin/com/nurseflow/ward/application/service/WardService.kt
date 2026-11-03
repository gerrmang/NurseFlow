package com.nurseflow.ward.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.config.BootstrapProperties
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.ward.application.model.BootstrapWardCommand
import com.nurseflow.ward.application.model.WardMembershipResult
import com.nurseflow.ward.application.model.WardRole
import com.nurseflow.ward.persistence.repository.WardJdbcRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.ZoneId
import java.util.UUID

@Service
class WardService(
    private val repo: WardJdbcRepository,
    private val bootstrap: BootstrapProperties,
    private val audit: AuditLog,
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
            )
        }

    @Transactional(readOnly = true)
    fun requireAdmin(wardId: UUID, subject: String) {
        val roles = repo.rolesIn(wardId, subject)
        if (roles.isEmpty()) throw BusinessException(ErrorCode.WARD_NOT_FOUND)
        if (WardRole.WARD_ADMIN.name !in roles) throw BusinessException(ErrorCode.WARD_ADMIN_REQUIRED)
    }

    /**
     * Demo-only: creates a ward and makes the caller its first WARD_ADMIN when the configured
     * bootstrap code matches. Every other member must join through an invitation.
     */
    @Transactional
    fun bootstrapWard(command: BootstrapWardCommand, subject: String): WardMembershipResult {
        val expected = bootstrap.code
        if (expected.isBlank()) throw BusinessException(ErrorCode.BOOTSTRAP_DISABLED)
        if (!MessageDigest.isEqual(expected.toByteArray(), command.bootstrapCode.toByteArray())) {
            throw BusinessException(ErrorCode.BOOTSTRAP_CODE_INVALID)
        }
        // Lock a stable singleton row so even concurrent requests on different app instances
        // can create at most one bootstrap ward. Keep the existing-ward check for pre-upgrade DBs.
        if (repo.lockBootstrapState() || repo.hasAnyWard()) {
            throw BusinessException(ErrorCode.BOOTSTRAP_DISABLED)
        }
        repo.markBootstrapConsumed()
        val zone = try {
            ZoneId.of(command.timeZone)
        } catch (_: DateTimeException) {
            throw BusinessException(ErrorCode.INVALID_TIME_ZONE)
        }
        val wardId = UUID.randomUUID()
        val name = command.name.trim()
        repo.insertWard(wardId, name, zone.id)
        repo.insertMembership(wardId, subject, WardRole.WARD_ADMIN.name, canViewWardCalendar = true)
        audit.record(wardId, subject, "WARD_BOOTSTRAPPED", wardId.toString())
        return WardMembershipResult(wardId, name, listOf(WardRole.WARD_ADMIN), canViewWardCalendar = true)
    }
}
