package cc.midolog.web.handler

import cc.midolog.web.exception.ApiException
import cc.midolog.web.exception.ErrorCode
import cc.midolog.web.response.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException

/**
 * 웹 계층에서 발생하는 예외를 포착해 표준 [ApiResponse] 에러 형태로 변환하는 전역 예외 처리기.
 *
 * Spring WebFlux 환경에서 동작하며, 일관된 에러 봉투 규격을 클라이언트에 제공한다.
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    /**
     * 비즈니스 예외([ApiException])를 처리하여 예외에 지정된 [ErrorCode]의 HTTP 상태 코드와 에러 응답을 반환한다.
     */
    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(e.errorCode.status)
            .body(ApiResponse.error(e.errorCode.name, e.message ?: e.errorCode.defaultMessage))

    /**
     * 요청 모델 검증(`@Valid`) 실패 시 발생하는 [WebExchangeBindException]을 처리하여 HTTP 400 상태 코드와 필드 에러 메시지를 취합해 반환한다.
     */
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

    /**
     * 처리되지 않은 기타 모든 예외([Exception])를 HTTP 500(INTERNAL_SERVER_ERROR)으로 표준화하여 반환한다.
     *
     * 내부 시스템 오류 메시지나 스택트레이스는 응답 본문에 노출하지 않고 표준 안내 메시지만 반환한다.
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiResponse<Nothing>> =
        ResponseEntity.status(ErrorCode.INTERNAL.status)
            .body(ApiResponse.error(ErrorCode.INTERNAL.name, ErrorCode.INTERNAL.defaultMessage))
}
