package com.nurseflow.ward.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.staff.domain.CareerPolicy
import com.nurseflow.ward.application.model.CreateWardCommand
import com.nurseflow.ward.application.model.WardMembershipResult
import com.nurseflow.ward.application.model.WardRole
import com.nurseflow.ward.persistence.repository.WardJdbcRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DateTimeException
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

@Service
class WardService(
    private val repo: WardJdbcRepository,
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

    @Transactional(readOnly = true)
    fun requireMember(wardId: UUID, subject: String) {
        if (repo.rolesIn(wardId, subject).isEmpty()) throw BusinessException(ErrorCode.WARD_NOT_FOUND)
    }

    /**
     * Any signed-in user may create a ward and becomes its first WARD_ADMIN. This grants access
     * to the new ward only; joining an existing ward still requires an invitation.
     */
    @Transactional
    fun createWard(command: CreateWardCommand, subject: String): WardMembershipResult {
        val zone = try {
            ZoneId.of(command.timeZone)
        } catch (_: DateTimeException) {
            throw BusinessException(ErrorCode.INVALID_TIME_ZONE)
        }
        // A new ward has the default career policy, so its ranks apply to the creator.
        if (command.careerStartedOn.isAfter(YearMonth.now(zone)) || command.careerStartedOn.year < 1950) {
            throw BusinessException(ErrorCode.INVALID_CAREER_START)
        }
        if (command.rank !in CareerPolicy.DEFAULT.ranks) throw BusinessException(ErrorCode.RANK_NOT_IN_POLICY)
        val wardId = UUID.randomUUID()
        val name = command.name.trim()
        repo.insertWard(wardId, name, zone.id)
        repo.insertMembership(wardId, subject, WardRole.WARD_ADMIN.name, canViewWardCalendar = true)
        repo.setMembershipProfile(wardId, subject, WardRole.WARD_ADMIN.name, command.careerStartedOn.toString(), command.rank)
        audit.record(wardId, subject, "WARD_CREATED", wardId.toString())
        return WardMembershipResult(wardId, name, listOf(WardRole.WARD_ADMIN), canViewWardCalendar = true)
    }
}
