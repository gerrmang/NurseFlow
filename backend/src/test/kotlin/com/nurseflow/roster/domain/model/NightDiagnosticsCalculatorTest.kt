package com.nurseflow.roster.domain.model

import com.nurseflow.policy.domain.NightPolicy
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

class NightDiagnosticsCalculatorTest {
    private val november = YearMonth.of(2026, 11)
    private val policy = NightPolicy()

    private fun nurse(pattern: WorkPattern = WorkPattern.GENERAL, rosterable: Boolean = true) =
        NightNurse(UUID.randomUUID(), "가상 간호사", pattern, rosterable)

    private fun term(nurse: NightNurse, start: String, end: String, planned: String = end) = NightDedicatedTerm(
        UUID.randomUUID(), UUID.randomUUID(), nurse.nurseId, YearMonth.parse(start), YearMonth.parse(planned),
        YearMonth.parse(end), Instant.EPOCH, "admin",
    )

    private fun one(n: NightNurse, nights: Int, sof: Int = 0, terms: List<NightDedicatedTerm> = emptyList(), p: NightPolicy = policy) =
        NightDiagnosticsCalculator.diagnose(november, p, listOf(n), mapOf(n.nurseId to nights), mapOf(n.nurseId to sof), terms)
            .nurses.single()

    @Test
    fun `general nurse needs one SOF from exactly six Nights and never a second one`() {
        val n = nurse()
        assertEquals(0, one(n, 5).sofRequiredDays)
        assertEquals(SofStatus.NOT_REQUIRED, one(n, 5).sofStatus)
        assertEquals(1, one(n, 6).sofRequiredDays)
        assertEquals(SofStatus.MISSING, one(n, 6).sofStatus)
        assertEquals(SofStatus.MET, one(n, 6, sof = 1).sofStatus)
        // Twelve Nights still need one SOF: no second day, no carry-over.
        assertEquals(1, one(n, 12).sofRequiredDays)
        assertEquals(SofStatus.EXCESS, one(n, 12, sof = 2).sofStatus)
        assertEquals(SofStatus.EXCESS, one(n, 5, sof = 1).sofStatus)
        assertNull(one(n, 6).dedicatedNightStatus)
        assertEquals(emptyList<NightIssue>(), one(n, 6).issues)
    }

    @Test
    fun `ward threshold and SOF days come from the policy, zero days turns SOF off`() {
        val n = nurse()
        val p = policy.copy(sofNightThreshold = 8, sofDays = 2)
        assertEquals(0, one(n, 7, p = p).sofRequiredDays)
        assertEquals(2, one(n, 8, p = p).sofRequiredDays)
        assertEquals(SofStatus.MISSING, one(n, 8, sof = 1, p = p).sofStatus)
        assertEquals(0, one(n, 20, p = policy.copy(sofDays = 0)).sofRequiredDays)
    }

    @Test
    fun `dedicated nurse is exempt from SOF and checked against the monthly Night minimum`() {
        val n = nurse(WorkPattern.NIGHT_ONLY)
        val terms = listOf(term(n, "2026-10", "2026-12"))
        val below = one(n, 14, terms = terms)
        assertEquals(SofStatus.DEDICATED_EXEMPT, below.sofStatus)
        assertEquals(0, below.sofRequiredDays)
        assertEquals(DedicatedNightStatus.BELOW_MINIMUM, below.dedicatedNightStatus)
        assertEquals(1, below.nightShortfall)
        assertEquals(3, below.dedicatedRunMonths)
        val met = one(n, 15, terms = terms)
        assertEquals(DedicatedNightStatus.MET, met.dedicatedNightStatus)
        assertEquals(0, met.nightShortfall)
        assertEquals(emptyList<NightIssue>(), met.issues)
    }

    @Test
    fun `term and work pattern mismatches are reported both ways`() {
        val general = nurse()
        val inTerm = one(general, 15, terms = listOf(term(general, "2026-11", "2027-01")))
        assertEquals(listOf(NightIssue.WORK_PATTERN_NOT_NIGHT_ONLY), inTerm.issues)
        assertEquals(SofStatus.DEDICATED_EXEMPT, inTerm.sofStatus)
        val nightOnly = nurse(WorkPattern.NIGHT_ONLY)
        val noTerm = one(nightOnly, 15)
        assertEquals(listOf(NightIssue.NO_DEDICATED_TERM), noTerm.issues)
        assertEquals(0, noTerm.dedicatedRunMonths)
        // A term that ended before this month no longer covers it.
        val ended = one(nightOnly, 15, terms = listOf(term(nightOnly, "2026-09", "2026-10", planned = "2026-11")))
        assertEquals(listOf(NightIssue.NO_DEDICATED_TERM), ended.issues)
    }

    @Test
    fun `back-to-back terms longer than the rotation are flagged and cancelled terms ignored`() {
        val n = nurse(WorkPattern.NIGHT_ONLY)
        val chained = listOf(term(n, "2026-09", "2026-11"), term(n, "2026-12", "2027-02"))
        val d = one(n, 15, terms = chained)
        assertEquals(6, d.dedicatedRunMonths)
        assertEquals(listOf(NightIssue.ROTATION_EXCEEDED), d.issues)
        val cancelled = term(n, "2026-12", "2026-11", planned = "2027-02")
        assertEquals(NightDedicatedTermStatus.CANCELLED, cancelled.status)
        assertEquals(3, one(n, 15, terms = listOf(chained[0], cancelled)).dedicatedRunMonths)
    }

    @Test
    fun `headcount counts rosterable nurses with a term, shortage and excess both show`() {
        val a = nurse(WorkPattern.NIGHT_ONLY)
        val b = nurse(WorkPattern.NIGHT_ONLY, rosterable = false)
        val c = nurse(WorkPattern.NIGHT_ONLY)
        val terms = listOf(term(a, "2026-11", "2027-01"), term(b, "2026-11", "2027-01"))
        val short = NightDiagnosticsCalculator.diagnose(november, policy, listOf(a, b, c), emptyMap(), emptyMap(), terms)
        assertEquals(DedicatedHeadcountStatus.SHORTAGE, short.headcount.status)
        assertEquals(listOf(a.nurseId), short.headcount.nurseIds)
        assertEquals(listOf(NightIssue.NOT_ROSTERABLE), short.nurses[1].issues)
        val excess = NightDiagnosticsCalculator.diagnose(
            november, policy.copy(dedicatedHeadcount = 1), listOf(a, c), emptyMap(), emptyMap(),
            listOf(term(a, "2026-11", "2027-01"), term(c, "2026-10", "2026-12")),
        )
        assertEquals(DedicatedHeadcountStatus.EXCESS, excess.headcount.status)
        assertEquals(2, excess.headcount.assigned)
        // The next 2 take over in February: January still has the old pair, February none yet.
        val handover = listOf(term(a, "2026-11", "2027-01"), term(c, "2026-11", "2027-01"))
        assertEquals(DedicatedHeadcountStatus.MET,
            NightDiagnosticsCalculator.diagnose(YearMonth.of(2027, 1), policy, listOf(a, c), emptyMap(), emptyMap(), handover).headcount.status)
        assertEquals(DedicatedHeadcountStatus.SHORTAGE,
            NightDiagnosticsCalculator.diagnose(YearMonth.of(2027, 2), policy, listOf(a, c), emptyMap(), emptyMap(), handover).headcount.status)
    }
}
