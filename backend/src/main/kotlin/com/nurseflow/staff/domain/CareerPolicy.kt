package com.nurseflow.staff.domain

import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * Ward-defined career stage rule. `stageThresholdMonths` are the four lower bounds of stages
 * 2..5 in months of experience: the default [12, 36, 72, 120] means
 * <1y = 1, 1–3y = 2, 3–6y = 3, 6–10y = 4, 10y+ = 5. Ranks are names only (no effect on stage yet).
 */
data class CareerPolicy(val stageThresholdMonths: List<Int>, val ranks: List<String>) {
    init {
        require(isValid(stageThresholdMonths, ranks)) { "invalid career policy" }
    }

    fun stageFor(experienceMonths: Int): Int = 1 + stageThresholdMonths.count { experienceMonths >= it }

    /** Number of stages (thresholds + 1); an admin stage override must lie in 1..stageCount. */
    val stageCount: Int get() = stageThresholdMonths.size + 1

    companion object {
        val DEFAULT = CareerPolicy(listOf(12, 36, 72, 120), listOf("팀장", "과장", "신규"))
        const val MAX_RANKS = 20
        const val MAX_RANK_LENGTH = 30

        fun isValid(thresholds: List<Int>, ranks: List<String>): Boolean =
            thresholds.size == 4 &&
                thresholds.all { it in 1..600 } &&
                thresholds.zipWithNext().all { (a, b) -> a < b } &&
                ranks.size in 1..MAX_RANKS &&
                ranks.all { it.isNotBlank() && it == it.trim() && it.length <= MAX_RANK_LENGTH } &&
                ranks.toSet().size == ranks.size
    }
}

/** Whole months from the career start month to [asOf]; never negative. */
fun experienceMonths(careerStartedOn: YearMonth, asOf: YearMonth): Int =
    ChronoUnit.MONTHS.between(careerStartedOn, asOf).toInt().coerceAtLeast(0)

enum class ProfileStatus {
    /** Nothing submitted yet. */
    MISSING,
    /** Submitted or changed since the last confirmation; excluded from scoring. */
    PENDING,
    /** Admin confirmed the current values. */
    CONFIRMED,
}
