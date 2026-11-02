package com.nurseflow.hospital.application.service

import com.nurseflow.hospital.application.error.HospitalErrorCode
import com.nurseflow.hospital.application.error.fail
import com.nurseflow.hospital.application.model.CreateWardCommand
import com.nurseflow.hospital.application.model.HospitalResult
import com.nurseflow.hospital.application.model.WardResult
import com.nurseflow.hospital.domain.HospitalAccess
import com.nurseflow.hospital.domain.OrganizationNames
import com.nurseflow.hospital.persistence.entity.DbHospital
import com.nurseflow.hospital.persistence.entity.DbHospitalWard
import com.nurseflow.hospital.persistence.entity.NewWard
import com.nurseflow.hospital.persistence.entity.ReplayLookup
import com.nurseflow.hospital.persistence.repository.HospitalJdbcRepository
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.util.UUID

/**
 * 병원 관리자의 병동 생성·조회 사용 사례. 모든 메서드는 Controller가 검증한 JWT subject를 명시적으로 받고
 * 병원 역할을 DB에서 다시 확인한다. 병동 관리자 역할은 병원 권한으로 취급하지 않는다.
 */
@Service
class HospitalWardService(
    private val repo: HospitalJdbcRepository,
    private val mapper: JsonMapper,
) {
    private val clock: Clock = Clock.systemUTC()

    @Transactional(readOnly = true)
    fun listMyHospitals(actor: String): List<HospitalResult> =
        repo.hospitalsAdministeredBy(actor).map { hospital ->
            HospitalResult(hospital.id, hospital.name, hospital.zone.id, repo.hospitalRoles(hospital.id, actor).map { it.name }.sorted())
        }

    @Transactional(readOnly = true)
    fun listWards(actor: String, hospitalId: UUID): List<WardResult> {
        requireHospitalAdmin(hospitalId, actor)
        return repo.wardsOfHospital(hospitalId).map(::wardResult)
    }

    @Transactional(readOnly = true)
    fun getWard(actor: String, hospitalId: UUID, wardId: UUID): WardResult {
        requireHospitalAdmin(hospitalId, actor)
        return repo.findWardInHospital(hospitalId, wardId)?.let(::wardResult) ?: fail(HospitalErrorCode.WARD_NOT_FOUND)
    }

    /**
     * 병동과 최초 병동 관리자 소속을 한 트랜잭션으로 만든다. 둘 중 하나라도 실패하면 둘 다 남지 않는다.
     * 병원 행 잠금으로 같은 병원의 생성 요청을 직렬화하고, 같은 Idempotency-Key 재시도는 처음 결과를 돌려준다.
     */
    @Transactional
    fun createWard(actor: String, command: CreateWardCommand): WardResult {
        val name = OrganizationNames.normalizeName(command.name) ?: fail(HospitalErrorCode.INVALID_WARD_NAME)
        val requestedZone = command.timeZone?.let { OrganizationNames.parseZone(it) ?: fail(HospitalErrorCode.INVALID_TIME_ZONE) }

        requireHospitalAdmin(command.hospitalId, actor)
        val hospital: DbHospital = repo.lockHospital(command.hospitalId) ?: fail(HospitalErrorCode.HOSPITAL_NOT_FOUND)

        val operation = "create-ward:${hospital.id}"
        val requestHash = hash(CreateWardFingerprint(actor, command.name, command.timeZone))
        when (val saved = repo.findReplay(command.idempotencyKey, operation, requestHash)) {
            is ReplayLookup.Match -> return mapper.readValue(saved.responseJson, WardResult::class.java)
            ReplayLookup.Mismatch -> fail(HospitalErrorCode.IDEMPOTENCY_KEY_REUSED)
            ReplayLookup.Absent -> Unit
        }

        if (repo.wardNameExists(hospital.id, name)) fail(HospitalErrorCode.WARD_NAME_TAKEN)
        val ward = NewWard(
            id = UUID.randomUUID(),
            hospitalId = hospital.id,
            name = name,
            zone = requestedZone ?: hospital.zone,
            createdAt = Instant.now(clock),
            createdBy = actor,
        )
        try {
            repo.insertWard(ward)
        } catch (_: DuplicateKeyException) {
            fail(HospitalErrorCode.WARD_NAME_TAKEN)
        }
        repo.insertWardAdmin(ward.id, actor)

        val result = WardResult(ward.id, ward.hospitalId, ward.name, ward.zone.id, ward.createdAt)
        try {
            repo.insertReplay(command.idempotencyKey, operation, requestHash, mapper.writeValueAsString(result))
        } catch (_: DuplicateKeyException) {
            // 같은 키가 다른 병원 요청에서 동시에 기록되었다. 이 트랜잭션의 병동·관리자 생성도 함께 되돌린다.
            fail(HospitalErrorCode.IDEMPOTENCY_KEY_REUSED)
        }
        return result
    }

    private fun requireHospitalAdmin(hospitalId: UUID, actor: String) {
        val access = HospitalAccess.decide(
            repo.hospitalRoles(hospitalId, actor),
            repo.hasWardMembershipInHospital(hospitalId, actor),
        )
        when (access) {
            HospitalAccess.ADMIN -> Unit
            HospitalAccess.AFFILIATED -> fail(HospitalErrorCode.HOSPITAL_ADMIN_REQUIRED)
            HospitalAccess.NONE -> fail(HospitalErrorCode.HOSPITAL_NOT_FOUND)
        }
    }

    private fun wardResult(ward: DbHospitalWard) =
        WardResult(ward.id, ward.hospitalId, ward.name, ward.zone.id, ward.createdAt)

    private fun hash(payload: Any): String = MessageDigest.getInstance("SHA-256")
        .digest(mapper.writeValueAsBytes(payload))
        .joinToString("") { "%02x".format(it) }

    /** 같은 키를 다른 사용자나 다른 입력으로 재사용하면 거부하기 위한 지문. */
    private data class CreateWardFingerprint(val actor: String, val name: String?, val timeZone: String?)
}
