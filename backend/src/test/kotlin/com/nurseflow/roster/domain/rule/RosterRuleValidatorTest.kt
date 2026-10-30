package com.nurseflow.roster.domain.rule

import com.nurseflow.roster.domain.model.ApprovedAbsence
import com.nurseflow.roster.domain.model.ApprovedAbsenceKind
import com.nurseflow.roster.domain.model.RuleSeverity
import com.nurseflow.roster.domain.model.ShiftAssignment
import com.nurseflow.roster.domain.model.ShiftKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class RosterRuleValidatorTest {
    private val validator = RosterRuleValidator()
    private val nurse = UUID.randomUUID()
    private val otherNurse = UUID.randomUUID()

    private fun day(date: String) = ShiftAssignment(
        UUID.randomUUID(), nurse, LocalDate.parse(date), ShiftKind.DAY,
        Instant.parse("${date}T07:00:00Z"), Instant.parse("${date}T15:00:00Z"),
    )

    private fun night(date: String) = ShiftAssignment(
        UUID.randomUUID(), nurse, LocalDate.parse(date), ShiftKind.NIGHT,
        Instant.parse("${date}T23:00:00Z"), Instant.parse("${date}T07:00:00Z".replace(date, nextDay(date))),
    )

    private fun nextDay(date: String): String = LocalDate.parse(date).plusDays(1).toString()

    // ── Night→Day 금지 ──

    @Test
    fun `REST_AFTER_NIGHT rejects day after night`() {
        val existing = listOf(night("2026-10-31"))
        val candidate = day("2026-11-01")
        val violations = validator.validate(candidate, existing, emptyList())
        assertTrue(violations.any { it.code == "REST_AFTER_NIGHT" })
        assertTrue(violations.all { it.severity == RuleSeverity.HARD })
    }

    @Test
    fun `REST_AFTER_NIGHT rejects night before day`() {
        val existing = listOf(day("2026-11-01"))
        val candidate = night("2026-10-31")
        val violations = validator.validate(candidate, existing, emptyList())
        assertTrue(violations.any { it.code == "REST_AFTER_NIGHT" })
    }

    @Test
    fun `night followed by day two days later is allowed`() {
        val existing = listOf(night("2026-10-30"))
        val candidate = day("2026-11-01")
        val violations = validator.validate(candidate, existing, emptyList())
        assertTrue(violations.isEmpty())
    }

    // ── 월 경계 Night→Day ──

    @Test
    fun `month boundary night to day is rejected`() {
        val existing = listOf(night("2026-10-31"))
        val candidate = day("2026-11-01")
        val violations = validator.validate(candidate, existing, emptyList())
        assertEquals(1, violations.size)
        assertEquals("REST_AFTER_NIGHT", violations[0].code)
    }

    // ── 시간 중복 금지 ──

    @Test
    fun `SHIFT_TIME_OVERLAP rejects overlapping shifts`() {
        val existing = listOf(day("2026-11-01"))
        val overlapping = ShiftAssignment(
            UUID.randomUUID(), nurse, LocalDate.parse("2026-11-01"), ShiftKind.OTHER,
            Instant.parse("2026-11-01T10:00:00Z"), Instant.parse("2026-11-01T18:00:00Z"),
        )
        val violations = validator.validate(overlapping, existing, emptyList())
        assertTrue(violations.any { it.code == "SHIFT_TIME_OVERLAP" })
    }

    @Test
    fun `adjacent shifts with no overlap are allowed`() {
        val existing = listOf(day("2026-11-01"))
        val adjacent = ShiftAssignment(
            UUID.randomUUID(), nurse, LocalDate.parse("2026-11-01"), ShiftKind.OTHER,
            Instant.parse("2026-11-01T15:00:00Z"), Instant.parse("2026-11-01T23:00:00Z"),
        )
        val violations = validator.validate(adjacent, existing, emptyList())
        assertTrue(violations.isEmpty())
    }

    @Test
    fun `different nurses can overlap`() {
        val existing = listOf(day("2026-11-01"))
        val otherNurseShift = ShiftAssignment(
            UUID.randomUUID(), otherNurse, LocalDate.parse("2026-11-01"), ShiftKind.DAY,
            Instant.parse("2026-11-01T07:00:00Z"), Instant.parse("2026-11-01T15:00:00Z"),
        )
        val violations = validator.validate(otherNurseShift, existing, emptyList())
        assertTrue(violations.isEmpty())
    }

    // ── 부재 겹침 금지 ──

    @Test
    fun `APPROVED_LEAVE_OVERLAP rejects shift during leave`() {
        val leave = listOf(
            ApprovedAbsence(
                nurse, ApprovedAbsenceKind.APPROVED_LEAVE,
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z"),
            ),
        )
        val violations = validator.validate(day("2026-11-01"), emptyList(), leave)
        assertTrue(violations.any { it.code == "APPROVED_LEAVE_OVERLAP" })
    }

    @Test
    fun `APPROVED_HALF_DAY_OVERLAP rejects shift during half-day`() {
        val halfDay = listOf(
            ApprovedAbsence(
                nurse, ApprovedAbsenceKind.APPROVED_HALF_DAY,
                Instant.parse("2026-11-01T07:00:00Z"), Instant.parse("2026-11-01T12:00:00Z"),
            ),
        )
        val violations = validator.validate(day("2026-11-01"), emptyList(), halfDay)
        assertTrue(violations.any { it.code == "APPROVED_HALF_DAY_OVERLAP" })
    }

    @Test
    fun `absence for other nurse does not block`() {
        val leave = listOf(
            ApprovedAbsence(
                otherNurse, ApprovedAbsenceKind.APPROVED_LEAVE,
                Instant.parse("2026-11-01T00:00:00Z"), Instant.parse("2026-11-02T00:00:00Z"),
            ),
        )
        val violations = validator.validate(day("2026-11-01"), emptyList(), leave)
        assertTrue(violations.isEmpty())
    }

    // ── 유효한 배치 허용 ──

    @Test
    fun `valid assignment with no conflicts returns empty violations`() {
        val violations = validator.validate(day("2026-11-01"), emptyList(), emptyList())
        assertTrue(violations.isEmpty())
    }

    // ── 무효 시간 구간 ──

    @Test
    fun `INVALID_SHIFT_INTERVAL rejects end before start`() {
        val invalid = ShiftAssignment(
            UUID.randomUUID(), nurse, LocalDate.parse("2026-11-01"), ShiftKind.DAY,
            Instant.parse("2026-11-01T15:00:00Z"), Instant.parse("2026-11-01T07:00:00Z"),
        )
        val violations = validator.validate(invalid, emptyList(), emptyList())
        assertEquals(1, violations.size)
        assertEquals("INVALID_SHIFT_INTERVAL", violations[0].code)
    }
}
