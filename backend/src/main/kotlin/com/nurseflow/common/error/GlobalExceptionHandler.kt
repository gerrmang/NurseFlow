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
    @ExceptionHandler(BusinessException::class)
    fun handle(ex: BusinessException): ResponseEntity<ApiProblem> = ResponseEntity
        .status(ex.errorCode.status)
        .contentType(MediaType.parseMediaType("application/problem+json"))
        .header("Cache-Control", "private, no-store")
        .body(
            ApiProblem(
                title = ex.errorCode.status.reasonPhrase,
                status = ex.errorCode.status.value(),
                code = ex.errorCode.name,
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
        .body(ApiProblem(title = "Bad Request", status = 400,
            code = ErrorCode.INVALID_REQUEST.name, detail = ErrorCode.INVALID_REQUEST.safeMessage))

    @ExceptionHandler(Exception::class)
    fun unexpected(): ResponseEntity<ApiProblem> = ResponseEntity.status(500)
        .contentType(MediaType.parseMediaType("application/problem+json"))
        .header("Cache-Control", "private, no-store")
        .body(ApiProblem(title = "Internal Server Error", status = 500,
            code = ErrorCode.INTERNAL_ERROR.name, detail = ErrorCode.INTERNAL_ERROR.safeMessage))
}
