package com.nurseflow.hospital.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Official hospital lookup used when an administrator picks the hospital for a new ward. */
@ConfigurationProperties("nurseflow.hospital")
data class HospitalProperties(val hira: Hira = Hira()) {
    /**
     * 건강보험심사평가원 병원정보서비스 on data.go.kr. `serviceKey` is the personal data.go.kr key:
     * either the "Decoding" form or the already URL-encoded "Encoding" form is accepted.
     */
    data class Hira(
        val baseUri: String = "https://apis.data.go.kr/B551182/hospInfoServicev2",
        val serviceKey: String = "",
        /** data.go.kr often takes several seconds per search. */
        val timeout: Duration = Duration.ofSeconds(10),
    ) {
        val configured: Boolean get() = serviceKey.isNotBlank()
    }
}

@Configuration
@EnableConfigurationProperties(HospitalProperties::class)
class HospitalConfiguration
