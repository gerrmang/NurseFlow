package com.nurseflow.ward.application.service

import com.nurseflow.common.audit.AuditLog
import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.ward.application.model.UpdateMembershipCommand
import com.nurseflow.ward.application.model.WardMemberResult
import com.nurseflow.ward.application.model.WardRole
import com.nurseflow.ward.persistence.repository.DbMemberRow
import com.nurseflow.ward.persistence.repository.WardJdbcRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Member administration of one ward. The ward must always keep at least one active WARD_ADMIN, so every
 * mutation takes the ward lock first and decides on what it reads afterwards.
 */
@Service
class MembershipService(
    private val repo: WardJdbcRepository,
    private val wards: WardService,
    private val audit: AuditLog,
) {
    @Transactional(readOnly = true)
    fun list(wardId: UUID, actor: String): List<WardMemberResult> {
        wards.requireAdmin(wardId, actor)
        return repo.members(wardId).groupBy { it.subject }.values.map { it.toResult() }
            .sortedWith(compareBy({ it.displayName }, { it.userId }))
    }

    @Transactional
    fun update(wardId: UUID, userId: UUID, command: UpdateMembershipCommand, actor: String): WardMemberResult {
        if (command.admin == null && command.active == null) throw BusinessException(ErrorCode.INVALID_REQUEST)
        wards.requireAdmin(wardId, actor)
        if (!repo.lockWard(wardId)) throw BusinessException(ErrorCode.WARD_NOT_FOUND)
        // A change committed while this request waited for the lock may have removed the caller's own access.
        wards.requireAdmin(wardId, actor)

        val subject = userId.toString()
        val rows = repo.memberRows(wardId, subject)
        if (rows.isEmpty()) throw BusinessException(ErrorCode.MEMBER_NOT_FOUND)
        val before = rows.toResult()
        val roles = before.roles.toSet()
        val isAdmin = WardRole.WARD_ADMIN in roles
        val wantsAdmin = command.admin ?: isAdmin
        val wantsActive = command.active ?: before.active

        if (!wantsAdmin && isAdmin && WardRole.WARD_MEMBER !in roles) {
            // Without a member role the account would keep no access at all; deactivation is the explicit way.
            throw BusinessException(ErrorCode.MEMBER_ROLE_REQUIRED)
        }
        val staysActiveAdmin = wantsAdmin && wantsActive
        val wasActiveAdmin = isAdmin && before.active
        if (wasActiveAdmin && !staysActiveAdmin && repo.countActiveAdminsExcept(wardId, subject) == 0) {
            throw BusinessException(ErrorCode.LAST_WARD_ADMIN)
        }

        if (wantsAdmin && !isAdmin) {
            repo.addRole(wardId, subject, WardRole.WARD_ADMIN.name, before.canViewWardCalendar, before.active)
            audit.record(wardId, actor, "MEMBER_ADMIN_GRANTED", subject)
        } else if (!wantsAdmin && isAdmin) {
            repo.deleteRole(wardId, subject, WardRole.WARD_ADMIN.name)
            audit.record(wardId, actor, "MEMBER_ADMIN_REVOKED", subject)
        }
        if (wantsActive != before.active) {
            repo.setMembershipActive(wardId, subject, wantsActive)
            if (!wantsActive) repo.deactivateLinkedNurse(wardId, subject)
            audit.record(wardId, actor, if (wantsActive) "MEMBER_REACTIVATED" else "MEMBER_DEACTIVATED", subject)
        }
        return repo.memberRows(wardId, subject).toResult()
    }

    private fun List<DbMemberRow>.toResult(): WardMemberResult {
        val first = first()
        return WardMemberResult(
            userId = first.subject,
            displayName = first.displayName,
            roles = map { WardRole.valueOf(it.role) }.distinct().sorted(),
            active = all { it.active },
            canViewWardCalendar = any { it.canViewWardCalendar },
            nurseId = first.nurseId,
        )
    }
}
