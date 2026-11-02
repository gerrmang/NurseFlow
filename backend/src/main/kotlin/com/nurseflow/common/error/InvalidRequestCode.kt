package com.nurseflow.common.error

/**
 * Keeps an existing public error code when a request body fails Bean Validation.
 *
 * Request DTOs whose invalid input already had a dedicated code before validation moved
 * to the HTTP boundary (for example `INVALID_MONTH`) declare it here. Other request bodies
 * fall back to `INVALID_REQUEST`. The safe field list is returned either way.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class InvalidRequestCode(val value: ErrorCode)
