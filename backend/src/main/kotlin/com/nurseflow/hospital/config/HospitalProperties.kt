package com.nurseflow.hospital.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.time.Duration

/** Official hospital lookup used when an administrator picks the hospital for a new ward. */
@ConfigurationProperties("nurseflow.hospital")
data class HospitalProperties(val hira: Hira = Hira(), val dataset: Dataset = Dataset()) {
    /**
     * Local copy of 건강보험심사평가원 「전국 병의원 및 약국 현황」 (data.go.kr 15051059, updated quarterly).
     * `path` is the downloaded zip, or its `병원정보서비스` CSV; it is imported at startup when its content changed.
     * `minRows` rejects a truncated file, and `maxDeactivationRatio` rejects a file that would deactivate more
     * than that share of the currently active list (real quarterly closures are about 1–2%).
     */
    data class Dataset(
        val path: String = "",
        val minRows: Int = 10_000,
        val maxDeactivationRatio: Double = 0.1,
    )

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
