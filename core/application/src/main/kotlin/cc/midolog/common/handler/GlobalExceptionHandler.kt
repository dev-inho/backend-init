package cc.midolog.common.handler

import cc.midolog.common.exception.ApiException
import cc.midolog.common.exception.ErrorCode
import cc.midolog.common.response.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException

/** 전역 예외 처리 — 예외를 표준 ApiResponse 에러 형태로 변환한다. */
@RestControllerAdvice
class GlobalExceptionHandler {

    /** 비즈니스 예외(ApiException)를 ErrorCode의 HTTP 상태로 매핑한다. */
    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(e.errorCode.status)
            .body(ApiResponse.error(e.errorCode.name, e.message ?: e.errorCode.defaultMessage))

    /** @Valid 검증 실패 → 400, 필드 에러 메시지 취합. */
    @ExceptionHandler(WebExchangeBindException::class)
    fun handleValidation(e: WebExchangeBindException): ResponseEntity<ApiResponse<Nothing>> {
        val message = e.fieldErrors.joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.status)
            .body(ApiResponse.error(ErrorCode.INVALID_INPUT.name, message.ifBlank { ErrorCode.INVALID_INPUT.defaultMessage }))
    }

    /** 처리되지 않은 예외는 500으로 표준화한다. */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(ErrorCode.INTERNAL.status)
            .body(ApiResponse.error(ErrorCode.INTERNAL.name, ErrorCode.INTERNAL.defaultMessage))
}
