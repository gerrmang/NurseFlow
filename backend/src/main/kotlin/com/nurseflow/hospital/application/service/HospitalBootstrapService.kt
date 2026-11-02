package com.nurseflow.hospital.application.service

import com.nurseflow.hospital.application.model.BootstrapHospitalCommand
import com.nurseflow.hospital.application.model.BootstrapHospitalResult
import com.nurseflow.hospital.domain.HospitalRole
import com.nurseflow.hospital.domain.OrganizationNames
import com.nurseflow.hospital.persistence.repository.HospitalJdbcRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 병원과 최초 병원 관리자를 등록하는 운영자 전용 초기 설정.
 *
 * HTTP endpoint가 없고 서버 설정으로만 실행된다. 같은 입력의 반복 실행은 기존 병원을 돌려준다.
 * 이미 있는 병원에 다른 subject를 관리자로 추가하지 않는다. 관리자 추가·변경은 후속 권한 관리 기능에서 감사 기록과 함께 다룬다.
 */
@Service
class HospitalBootstrapService(private val repo: HospitalJdbcRepository) {
    private val clock: Clock = Clock.systemUTC()

    @Transactional
    fun bootstrap(command: BootstrapHospitalCommand): BootstrapHospitalResult {
        val name = requireNotNull(OrganizationNames.normalizeName(command.name)) { "병원 이름은 1~100자여야 합니다." }
        val zone = requireNotNull(OrganizationNames.parseZone(command.timeZone)) { "병원 시간대가 올바르지 않습니다." }
        val subject = command.adminSubject?.trim()
        require(!subject.isNullOrEmpty() && subject.length <= 255) { "최초 병원 관리자 subject가 올바르지 않습니다." }

        repo.findHospitalByName(name)?.let { existing ->
            check(HospitalRole.HOSPITAL_ADMIN in repo.hospitalRoles(existing.id, subject)) {
                "같은 이름의 병원이 이미 다른 관리자로 등록되어 있습니다. 초기 설정으로 관리자를 추가하지 않습니다."
            }
            return BootstrapHospitalResult(existing.id, created = false)
        }

        val now = Instant.now(clock)
        val hospitalId = UUID.randomUUID()
        repo.insertHospital(hospitalId, name, zone, now)
        repo.insertHospitalMembership(hospitalId, subject, HospitalRole.HOSPITAL_ADMIN, now)
        return BootstrapHospitalResult(hospitalId, created = true)
    }
}
