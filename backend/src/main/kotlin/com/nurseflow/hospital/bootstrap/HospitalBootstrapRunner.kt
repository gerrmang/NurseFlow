package com.nurseflow.hospital.bootstrap

import com.nurseflow.hospital.application.model.BootstrapHospitalCommand
import com.nurseflow.hospital.application.service.HospitalBootstrapService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * `nurseflow.bootstrap.hospital.name`이 설정된 경우에만 기동 시 병원과 최초 병원 관리자를 등록한다.
 * 서버 운영자만 바꿀 수 있는 환경 설정을 사용하며 네트워크로 노출되는 경로가 없다.
 * 로그에는 병원 ID와 생성 여부만 남기고 관리자 subject는 남기지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "nurseflow.bootstrap.hospital", name = ["name"])
class HospitalBootstrapRunner(
    private val service: HospitalBootstrapService,
    @Value("\${nurseflow.bootstrap.hospital.name}") private val name: String,
    @Value("\${nurseflow.bootstrap.hospital.time-zone:Asia/Seoul}") private val timeZone: String,
    @Value("\${nurseflow.bootstrap.hospital.admin-subject:}") private val adminSubject: String,
) : ApplicationRunner {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val result = service.bootstrap(BootstrapHospitalCommand(name, timeZone, adminSubject))
        log.info("병원 초기 설정 확인: hospitalId={}, created={}", result.hospitalId, result.created)
    }
}
