package cc.midolog.logging

/**
 * 로깅 전반에서 사용하는 표준 MDC 키 상수 모음.
 *
 * core:gateway, support:logging, support:web 등 여러 모듈이 동일한 키 문자열을 참조하도록 일원화한다.
 */
object LoggingMdc {
    /** HTTP 요청 추적을 위한 표준 식별자 헤더이자 MDC 키 이름(`X-Request-Id`). */
    const val REQUEST_ID = "X-Request-Id"
}
