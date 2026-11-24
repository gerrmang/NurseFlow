package com.nurseflow.common.idempotency

sealed interface ReplayLookup<out T> {
    data object Missing : ReplayLookup<Nothing>
    data object Conflict : ReplayLookup<Nothing>
    data class Found<T>(val value: T) : ReplayLookup<T>
}
