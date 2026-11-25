package com.nurseflow.policy.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import java.time.Duration

/**
 * Korean public holidays from 한국천문연구원 특일 정보 (data.go.kr 15012690, `getRestDeInfo`).
 * Without [serviceKey] nothing is fetched and wards enter holidays by hand. [refreshCron] refreshes
 * [monthsAhead] months from the current one (temporary holidays are announced at short notice); "-" disables it.
 */
@ConfigurationProperties("nurseflow.holiday")
data class HolidayProperties(
    val baseUri: String = "https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService",
    val serviceKey: String = "",
    val timeout: Duration = Duration.ofSeconds(10),
    val refreshCron: String = "0 30 3 * * *",
    val refreshZone: String = "Asia/Seoul",
    val monthsAhead: Int = 12,
) {
    val configured: Boolean get() = serviceKey.isNotBlank()
}

@Configuration
@EnableScheduling
@EnableConfigurationProperties(HolidayProperties::class)
class HolidayConfiguration
