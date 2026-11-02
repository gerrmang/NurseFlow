package com.nurseflow.staff.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.YearMonth

class CareerPolicyTest {
    private val policy = CareerPolicy.DEFAULT

    @Test
    fun `default boundaries are under 1y, 1-3y, 3-6y, 6-10y, 10y plus`() {
        val expected = mapOf(0 to 1, 11 to 1, 12 to 2, 35 to 2, 36 to 3, 71 to 3, 72 to 4, 119 to 4, 120 to 5, 400 to 5)
        expected.forEach { (months, stage) -> assertEquals(stage, policy.stageFor(months), "$months months") }
    }

    @Test
    fun `experience counts whole months and never goes negative`() {
        assertEquals(12, experienceMonths(YearMonth.of(2025, 11), YearMonth.of(2026, 11)))
        assertEquals(11, experienceMonths(YearMonth.of(2025, 12), YearMonth.of(2026, 11)))
        assertEquals(0, experienceMonths(YearMonth.of(2027, 1), YearMonth.of(2026, 11)))
    }

    @Test
    fun `policy must have four increasing thresholds and distinct ranks`() {
        assertTrue(CareerPolicy.isValid(listOf(6, 24, 60, 120), listOf("팀장", "과장", "신규")))
        assertFalse(CareerPolicy.isValid(listOf(12, 36, 72), listOf("신규")))
        assertFalse(CareerPolicy.isValid(listOf(12, 12, 72, 120), listOf("신규")))
        assertFalse(CareerPolicy.isValid(listOf(12, 36, 72, 120), listOf("신규", "신규")))
        assertFalse(CareerPolicy.isValid(listOf(12, 36, 72, 120), emptyList()))
        assertFalse(CareerPolicy.isValid(listOf(0, 36, 72, 120), listOf("신규")))
    }
}
