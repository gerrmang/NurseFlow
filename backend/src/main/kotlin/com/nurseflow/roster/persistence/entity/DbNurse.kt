package com.nurseflow.roster.persistence.entity

import java.util.UUID
import com.nurseflow.roster.domain.model.WorkPattern

data class DbNurse(val id: UUID, val qualifications: Set<String>, val active: Boolean, val workPattern: WorkPattern = WorkPattern.GENERAL)
