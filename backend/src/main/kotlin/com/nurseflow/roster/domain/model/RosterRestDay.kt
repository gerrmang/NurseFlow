package com.nurseflow.roster.domain.model

import java.time.LocalDate
import java.util.UUID

enum class RestDayKind { OF, SOF }

data class RosterRestDay(val nurseId: UUID, val date: LocalDate, val kind: RestDayKind)
