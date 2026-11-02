package com.nurseflow.common.error

import com.fasterxml.jackson.annotation.JsonInclude

data class ApiProblem(
    val type: String = "about:blank",
    val title: String,
    val status: Int,
    val code: String,
    val detail: String,
    val currentRevision: Int? = null,
    val ruleViolations: List<Any> = emptyList(),
    @field:JsonInclude(JsonInclude.Include.NON_EMPTY)
    val fieldErrors: List<FieldErrorView> = emptyList(),
)

/** A safe field-level validation message. The rejected value is never echoed back. */
data class FieldErrorView(val field: String, val message: String)
