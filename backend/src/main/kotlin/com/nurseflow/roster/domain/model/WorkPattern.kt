package com.nurseflow.roster.domain.model

/** Restricted patterns allow only explicit D/E/N kinds, including legacy OTHER records. */
enum class WorkPattern {
    GENERAL, DAY_EVENING_ONLY, NIGHT_ONLY;

    fun allows(kind: ShiftKind): Boolean = when (this) {
        GENERAL -> true
        DAY_EVENING_ONLY -> kind == ShiftKind.DAY || kind == ShiftKind.EVENING
        NIGHT_ONLY -> kind == ShiftKind.NIGHT
    }
}
