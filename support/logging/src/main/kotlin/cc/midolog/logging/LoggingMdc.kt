package cc.midolog.logging

/**
 * 로깅 전반에서 사용하는 표준 MDC 키 상수 모음.
 * gateway, support:logging 등 여러 모듈이 동일한 키 문자열을 참조하도록 일원화한다.
 */
object LoggingMdc {
    const val REQUEST_ID = "X-Request-Id"
}
