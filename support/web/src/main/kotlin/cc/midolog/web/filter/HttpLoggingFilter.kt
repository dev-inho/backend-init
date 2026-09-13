package cc.midolog.web.filter

import cc.midolog.logging.LoggingMdc
import kotlin.time.TimeSource
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
 *
 * 시간 측정 및 TimeSource 주입:
 * 벽시계 대신 단조 시계인 [TimeSource]를 주입받아 소요 시간을 측정한다.
 * NTP 보정이나 시스템 시각 변경에 의한 음수·왜곡 측정을 방지하며, 테스트 환경에서는 [kotlin.time.TestTimeSource]를
 * 주입해 결정적인 소요 시간 검증이 가능하다.
 */
@Component
@Order(-2)
class HttpLoggingFilter(
    private val timeSource: TimeSource = TimeSource.Monotonic,
) : WebFilter {

    companion object {
        private val log = LoggerFactory.getLogger(HttpLoggingFilter::class.java)
    }

    /** 요청 시작 시각을 기록하고, 응답 완료 시 메타데이터만 INFO 로그로 남긴다. */
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val mark = timeSource.markNow()
        val request = exchange.request

        return chain.filter(exchange)
            .doFinally {
                val duration = mark.elapsedNow().inWholeMilliseconds
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
