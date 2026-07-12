package cc.midolog.common.handler

import cc.midolog.common.exception.ApiException
import cc.midolog.common.exception.ErrorCode
import cc.midolog.common.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException

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

    /**
     * 프레임워크가 던지는 [ResponseStatusException](malformed JSON → 400,
     * 지원하지 않는 Content-Type → 415 등)의 상태 코드를 보존한다. 내부
     * 예외 메시지·스택트레이스는 응답 본문에 노출하지 않고, 상태 코드에 맞는
     * 표준 메시지만 ApiResponse 규약으로 반환한다.
     */
    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatus(e: ResponseStatusException): ResponseEntity<ApiResponse<Nothing>> {
        val status = e.statusCode
        val (code, message) = when (status.value()) {
            HttpStatus.BAD_REQUEST.value() -> ErrorCode.INVALID_INPUT.name to ErrorCode.INVALID_INPUT.defaultMessage
            HttpStatus.UNSUPPORTED_MEDIA_TYPE.value() -> "UNSUPPORTED_MEDIA_TYPE" to "지원하지 않는 미디어 타입입니다"
            else -> "REQUEST_ERROR" to "요청을 처리할 수 없습니다"
        }
        return ResponseEntity.status(status).body(ApiResponse.error(code, message))
    }

    /** 처리되지 않은 예외는 500으로 표준화한다. */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(ErrorCode.INTERNAL.status)
            .body(ApiResponse.error(ErrorCode.INTERNAL.name, ErrorCode.INTERNAL.defaultMessage))
}
