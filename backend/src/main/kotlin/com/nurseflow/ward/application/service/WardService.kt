package com.nurseflow.ward.application.service

import com.nurseflow.common.error.BusinessException
import com.nurseflow.common.error.ErrorCode
import com.nurseflow.ward.application.model.WardMembershipResult
import com.nurseflow.ward.application.model.WardRole
import com.nurseflow.ward.persistence.repository.WardJdbcRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class WardService(
    private val repo: WardJdbcRepository,
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

}
