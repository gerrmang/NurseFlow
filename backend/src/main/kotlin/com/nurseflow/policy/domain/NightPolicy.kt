package com.nurseflow.policy.domain

/**
 * Ward SOF and Night-dedicated settings (A15). A general nurse with at least [sofNightThreshold] Nights in a month
 * needs [sofDays] SOF days in that same month; nothing carries over to the next month. [dedicatedHeadcount] nurses
 * are Night-dedicated at a time, each for [dedicatedRotationMonths] consecutive months, with at least
 * [dedicatedMinNights] Nights a month. The defaults are the user's demo values, never legal limits.
 */
data class NightPolicy(
    val sofNightThreshold: Int = 6,
    val sofDays: Int = 1,
    val dedicatedHeadcount: Int = 2,
    val dedicatedRotationMonths: Int = 3,
    val dedicatedMinNights: Int = 15,
) {
    fun isValid(): Boolean = sofNightThreshold in 1..MAX_DAYS && sofDays in 0..MAX_DAYS &&
        dedicatedHeadcount in 0..MAX_HEADCOUNT && dedicatedRotationMonths in 1..MAX_ROTATION_MONTHS &&
        dedicatedMinNights in 0..MAX_DAYS

    companion object {
        const val MAX_DAYS = 31
        const val MAX_HEADCOUNT = 50
        const val MAX_ROTATION_MONTHS = 12
    }
}
