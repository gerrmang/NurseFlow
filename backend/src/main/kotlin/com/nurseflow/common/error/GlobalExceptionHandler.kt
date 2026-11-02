package com.nurseflow.common.error

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(ApiException::class)
    fun handle(ex: ApiException): ResponseEntity<ApiProblem> = ResponseEntity
        .status(ex.status)
        .contentType(MediaType.parseMediaType("application/problem+json"))
        .header("Cache-Control", "private, no-store")
        .body(
            ApiProblem(
                title = ex.status.reasonPhrase,
                status = ex.status.value(),
                code = ex.code,
                detail = ex.safeDetail,
                currentRevision = ex.currentRevision,
                ruleViolations = ex.ruleViolations,
            ),
        )

    @ExceptionHandler(
        HttpMessageNotReadableException::class,
        MissingRequestHeaderException::class,
        MethodArgumentTypeMismatchException::class,
    )
    fun malformed(): ResponseEntity<ApiProblem> = ResponseEntity.status(400)
        .contentType(MediaType.parseMediaType("application/problem+json"))
        .header("Cache-Control", "private, no-store")
        .body(
            ApiProblem(
                title = "Bad Request",
                status = 400,
                code = "INVALID_REQUEST",
                detail = "요청 형식 또는 필드 값이 올바르지 않습니다.",
            ),
        )
}
