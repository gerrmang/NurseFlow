package com.nurseflow.roster.persistence.entity

import java.util.UUID

data class DbNurse(val id: UUID, val qualifications: Set<String>, val active: Boolean, val teamId: UUID?, val seniorEligible: Boolean)
