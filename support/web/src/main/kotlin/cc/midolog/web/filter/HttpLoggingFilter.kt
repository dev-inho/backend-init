package cc.midolog.web.filter

import cc.midolog.logging.LoggingMdc
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 요청/응답 메타데이터 로깅 필터.
 * - method, path, status, duration, X-Request-Id만 INFO로 기록한다.
 * - 요청/응답 바디·쿼리스트링은 PII 노출 방지를 위해 절대 로깅하지 않는다.
 * - stateless: 요청 시작 시각을 로컬 변수로만 사용한다.
 *
 * 최외곽 순서로 등록해 인증 거부(401)·기타 단축 응답을 포함한 모든 요청을
 * 결정적으로 관측한다. [RequestIdFilter](Order 0)가 응답 헤더에 세팅한
 * X-Request-Id를 완료 시점(doFinally)에 읽는다.
 */
@Component
@Order(-2)
class HttpLoggingFilter : WebFilter {

    companion object {
        private val log = LoggerFactory.getLogger(HttpLoggingFilter::class.java)
    }

    /** 요청 시작 시각을 기록하고, 응답 완료 시 메타데이터만 INFO 로그로 남긴다. */
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val startedAt = System.currentTimeMillis()
        val request = exchange.request

        return chain.filter(exchange)
            .doFinally {
                val duration = System.currentTimeMillis() - startedAt
                val requestId = exchange.response.headers.getFirst(LoggingMdc.REQUEST_ID)
                    ?: request.headers.getFirst(LoggingMdc.REQUEST_ID)
                log.info(
                    "method={} path={} status={} durationMs={} requestId={}",
                    request.method,
                    request.path.value(),
                    exchange.response.statusCode?.value(),
                    duration,
                    requestId,
                )
            }
    }
}
