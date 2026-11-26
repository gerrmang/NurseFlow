package com.nurseflow.app.feature.schedule

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object ScheduleTime {
    private val display = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm")
    fun local(instant: String, zone: String): String = Instant.parse(instant).atZone(ZoneId.of(zone)).format(display)
    fun instant(local: String, zone: String): String {
        require(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}").matches(local)) { "날짜·시각은 YYYY-MM-DDTHH:mm으로 입력해 주세요." }
        val time = LocalDateTime.parse(local)
        val offsets = ZoneId.of(zone).rules.getValidOffsets(time)
        require(offsets.size == 1) { "병동 시간대에서 존재하지 않거나 중복되는 시각이에요. 다른 시각을 선택해 주세요." }
        return time.toInstant(offsets.single()).toString()
    }
    /** Preserve the original nanoseconds and DST occurrence for endpoints the user did not edit. */
    fun endpoint(input: String, original: String?, zone: String): String =
        if (original != null && input == local(original, zone)) original else instant(input, zone)
}
