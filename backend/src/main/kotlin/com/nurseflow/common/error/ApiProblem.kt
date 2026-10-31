package com.nurseflow.common.error

data class ApiProblem(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val code: String,
    val detail: String,
    val currentRevision: Int? = null,
    val ruleViolations: List<Any> = emptyList(),
)
