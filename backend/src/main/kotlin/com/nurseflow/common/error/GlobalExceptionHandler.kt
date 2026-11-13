package com.nurseflow.common.error

import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import tools.jackson.core.JacksonException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException::class)
    fun handle(ex: BusinessException): ResponseEntity<ApiProblem> = problem(
        ex.errorCode,
        detail = ex.safeDetail,
        currentRevision = ex.currentRevision,
        ruleViolations = ex.ruleViolations,
    )

    /** The transaction rolled back; the key belongs to a request that committed first elsewhere. */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<ApiProblem> {
        val mappingError = generateSequence(ex as Throwable) { it.cause }
            .filterIsInstance<JacksonException>()
            .firstOrNull()
        val path = mappingError?.path.orEmpty().mapNotNull { reference ->
            reference.propertyName?.takeIf(String::isNotBlank)
                ?: reference.index.takeIf { it >= 0 }?.let { "[$it]" }
        }.joinToString(".").replace(".[", "[")
        val field = path.ifBlank { "requestBody" }
        return problem(
            ErrorCode.INVALID_REQUEST,
            fieldErrors = listOf(FieldErrorView(field, "요청 본문의 필수 값 또는 형식을 확인해 주세요.")),
        )
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun missingHeader(ex: MissingRequestHeaderException): ResponseEntity<ApiProblem> = problem(
        ErrorCode.INVALID_REQUEST,
        fieldErrors = listOf(FieldErrorView(ex.headerName, "필수 값입니다.")),
    )

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParameter(ex: MissingServletRequestParameterException): ResponseEntity<ApiProblem> = problem(
        ErrorCode.INVALID_REQUEST,
        fieldErrors = listOf(FieldErrorView(ex.parameterName, "필수 값입니다.")),
    )

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun invalidParameter(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiProblem> = problem(
        ErrorCode.INVALID_REQUEST,
        fieldErrors = listOf(FieldErrorView(ex.name, "형식이 올바르지 않습니다.")),
    )

    /** `@Valid @RequestBody` failures. Field names are returned, rejected values are not. */
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun invalidBody(ex: MethodArgumentNotValidException): ResponseEntity<ApiProblem> {
        val target = ex.bindingResult.target?.javaClass ?: ex.parameter.parameterType
        val code = target.getAnnotation(InvalidRequestCode::class.java)?.value ?: ErrorCode.INVALID_REQUEST
        val fields = ex.bindingResult.fieldErrors.map { it.toView() } +
            ex.bindingResult.globalErrors.map { FieldErrorView(it.objectName, safeMessage(it.code)) }
        return problem(code, fieldErrors = fields.distinct().sortedBy { it.field })
    }

    /** Constraint failures on request parameters and headers. */
    @ExceptionHandler(HandlerMethodValidationException::class)
    fun invalidParameters(ex: HandlerMethodValidationException): ResponseEntity<ApiProblem> {
        val fields = ex.parameterValidationResults.flatMap { result ->
            val name = result.methodParameter.parameterName ?: "parameter"
            result.resolvableErrors.map { FieldErrorView(name, safeMessage(it.codes?.lastOrNull())) }
        }
        return problem(ErrorCode.INVALID_REQUEST, fieldErrors = fields.distinct().sortedBy { it.field })
    }

    @ExceptionHandler(Exception::class)
    fun unexpected(): ResponseEntity<ApiProblem> = problem(ErrorCode.INTERNAL_ERROR)

    private fun problem(
        code: ErrorCode,
        detail: String = code.safeMessage,
        currentRevision: Int? = null,
        ruleViolations: List<Any> = emptyList(),
        fieldErrors: List<FieldErrorView> = emptyList(),
    ): ResponseEntity<ApiProblem> = ResponseEntity
        .status(code.status)
        .contentType(PROBLEM_JSON)
        .header("Cache-Control", "private, no-store")
        .body(
            ApiProblem(
                title = code.status.reasonPhrase,
                status = code.status.value(),
                code = code.name,
                detail = detail,
                currentRevision = currentRevision,
                ruleViolations = ruleViolations,
                fieldErrors = fieldErrors,
            ),
        )

    private fun FieldError.toView() = FieldErrorView(field, safeMessage(code))

    companion object {
        private val PROBLEM_JSON = MediaType.parseMediaType("application/problem+json")

        /**
         * Fixed messages per constraint type. Annotation messages and rejected values are not
         * forwarded so user input, names or leave details never appear in an error response.
         */
        internal fun safeMessage(constraint: String?): String = when (constraint?.substringAfterLast('.')) {
            "NotNull" -> "필수 값입니다."
            "NotBlank", "NotEmpty" -> "값을 입력해 주세요."
            "Size" -> "길이 또는 개수가 허용 범위를 벗어났습니다."
            "Pattern" -> "형식이 올바르지 않습니다."
            "Min", "Max", "Positive", "PositiveOrZero", "DecimalMin", "DecimalMax" -> "허용 범위를 벗어난 값입니다."
            "AssertTrue", "AssertFalse" -> "조건을 만족하지 않습니다."
            else -> "값이 올바르지 않습니다."
        }
    }
}
