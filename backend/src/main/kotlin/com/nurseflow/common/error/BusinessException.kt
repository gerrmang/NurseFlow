package com.nurseflow.common.error

class BusinessException(
    val errorCode: ErrorCode,
    val currentRevision: Int? = null,
    val ruleViolations: List<Any> = emptyList(),
    val safeDetail: String = errorCode.safeMessage,
) : RuntimeException(safeDetail)
