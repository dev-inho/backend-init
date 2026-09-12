package cc.midolog.web.exception

import org.springframework.http.HttpStatus

/**
 * 표준 API 에러 코드 열거형.
 *
 * 대응하는 HTTP 상태([status])와 기본 사용자 안내 메시지([defaultMessage])를 함께 정의한다.
 * [FORBIDDEN]은 현재 main 코드 소비자는 없으나(docs/DEAD_CODE_CANDIDATES.md #7),
 * HTTP 403 인가 실패 처리를 위한 표준 에러 코드로 유지된다.
 */
enum class ErrorCode(val status: HttpStatus, val defaultMessage: String) {
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "리소스를 찾을 수 없습니다"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "페이로드가 너무 큽니다"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 미디어 타입입니다"),
    INTERNAL(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다"),
}
