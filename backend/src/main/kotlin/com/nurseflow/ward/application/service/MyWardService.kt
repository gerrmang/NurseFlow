package com.nurseflow.ward.application.service

import com.nurseflow.ward.persistence.repository.WardMembershipJdbcRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

data class MyWardResult(
    val wardId: UUID,
    val wardName: String,
    val timeZone: String,
    val hospitalId: UUID?,
    val hospitalName: String?,
    val roles: List<String>,
    val canViewWardCalendar: Boolean,
)

/**
 * 병동 선택 화면용 본인 소속 조회. 병원 관리자 역할은 병동 소속을 만들지 않으므로 여기에 나타나지 않고,
 * 병동 구성원에게 병원 권한이나 다른 병동을 노출하지 않는다.
 */
@Service
class MyWardService(private val repo: WardMembershipJdbcRepository) {
    @Transactional(readOnly = true)
    fun myWards(subject: String): List<MyWardResult> =
        repo.membershipsOf(subject).groupBy { it.wardId }.values.map { rows ->
            val first = rows.first()
            MyWardResult(
                wardId = first.wardId,
                wardName = first.wardName,
                timeZone = first.timeZone,
                hospitalId = first.hospitalId,
                hospitalName = first.hospitalName,
                roles = rows.map { it.role }.distinct().sorted(),
                canViewWardCalendar = rows.any { it.canViewWardCalendar },
            )
        }
}
