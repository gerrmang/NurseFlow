package com.nurseflow.common.error

import org.springframework.http.HttpStatus

class ApiException(
    val status: HttpStatus,
    val code: String,
    val safeDetail: String,
    val currentRevision: Int? = null,
    val ruleViolations: List<Any> = emptyList(),
) : RuntimeException(safeDetail)
