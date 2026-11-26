package com.nurseflow.app.feature.schedule

import org.junit.Assert.*
import org.junit.Test

class ScheduleTimeTest {
    @Test fun convertsUsingWardZoneAcrossMidnight() {
        assertEquals("2026-11-01T15:00:00Z", ScheduleTime.instant("2026-11-02T00:00", "Asia/Seoul"))
        assertEquals("2026-11-02T00:00", ScheduleTime.local("2026-11-01T15:00:00Z", "+09:00"))
    }
    @Test fun unchangedEndpointPreservesNanosecondsAndOffset() {
        val original = "2026-11-05T09:00:30.123456789+09:00"
        assertEquals(original, ScheduleTime.endpoint("2026-11-05T09:00", original, "Asia/Seoul"))
        assertEquals("2026-11-05T00:01:00Z", ScheduleTime.endpoint("2026-11-05T09:01", original, "Asia/Seoul"))
    }
    @Test fun preservesBothOccurrencesOfUneditedDstTime() {
        listOf("2026-11-01T05:30:00Z", "2026-11-01T06:30:00Z").forEach {
            assertEquals(it, ScheduleTime.endpoint("2026-11-01T01:30", it, "America/New_York"))
        }
    }
    @Test fun rejectsNewSkippedOrAmbiguousTime() {
        listOf("2026-11-01T01:30", "2026-03-08T02:30").forEach { input ->
            assertTrue(runCatching { ScheduleTime.instant(input, "America/New_York") }.isFailure)
        }
    }
    @Test fun rejectsImpossibleDatesAndUnrecognizedZones() {
        assertTrue(runCatching { ScheduleTime.instant("2026-02-30T12:00", "Asia/Seoul") }.isFailure)
        assertTrue(runCatching { ScheduleTime.instant("2026-11-05T12:00", "Invalid/Zone") }.isFailure)
    }
    @Test fun changingOneEndpointLeavesOtherPrecisionIntact() {
        val original = "2026-11-05T04:00:00.000000001Z"
        assertEquals(original, ScheduleTime.endpoint("2026-11-05T13:00", original, "Asia/Seoul"))
        assertEquals("2026-11-05T00:00:00Z", ScheduleTime.endpoint("2026-11-05T09:00", null, "Asia/Seoul"))
    }
}
