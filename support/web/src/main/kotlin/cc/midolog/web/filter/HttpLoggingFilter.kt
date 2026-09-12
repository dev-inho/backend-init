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
 * HTTP 요청/응답 메타데이터를 INFO 레벨 로그로 기록하는 관측 필터.
 *
 * Spring 컴포넌트 스캔을 통해 gateway와 application 양쪽 모듈에 자동 등록된다.
 * `@Order(-2)`로 최외곽에 위치하여 인증 거부(401)나 한도 초과(429) 등 조기 종료 응답을 포함한 모든 인입 요청을
 * 결정적으로 관측한다. 개인식별정보(PII) 노출을 방지하기 위해 요청/응답 본문과 쿼리스트링은 기록하지 않으며,
 * 메서드, 경로, 상태 코드, 소요 시간, 요청 ID만 선별 기록한다.
 * [RequestIdFilter](@Order(0))가 응답 헤더에 세팅한 식별자를 완료 시점(`doFinally`)에 읽어 출력한다.
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
