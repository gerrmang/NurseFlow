package com.nurseflow.roster.api.dto.request

import java.util.UUID

/** Months are `YYYY-MM`. Without [endMonth] the term runs for the ward's rotation length from [startMonth]. */
data class CreateNightDedicatedTermRequest(
    val nurseId: UUID,
    val startMonth: String,
    val endMonth: String? = null,
)

/** The last dedicated month. Only an earlier end is accepted; the month before the start cancels the term. */
data class EndNightDedicatedTermRequest(val lastMonth: String)
