package cc.midolog.common.exception

import org.springframework.http.HttpStatus

/** 표준 에러 코드. HTTP 상태와 기본 메시지를 함께 가진다. */
enum class ErrorCode(val status: HttpStatus, val defaultMessage: String) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다"),
}
