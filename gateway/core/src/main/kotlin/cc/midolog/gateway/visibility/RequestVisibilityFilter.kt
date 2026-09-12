package cc.midolog.gateway.visibility

import cc.midolog.logging.LoggingMdc
import java.time.Clock
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 요청 완료 후 메타데이터를 이벤트 저장소에 기록하는 가시성 필터.
 *
 * `gateway.request-visibility.enabled=true` 설정 시에만 빈으로 등록된다.
 *
 * 필터 체인 순서 계약:
 * -2 HttpLoggingFilter(support:web) → -1 AuthTokenRateLimitFilter → 0 RequestIdFilter(support:web) → 1 JwtAuthFilter → 100 RequestVisibilityFilter
 *
 * 앞뒤 순서와 위치 이유:
 * 앞에는 cc.midolog.web.filter.HttpLoggingFilter(@Order(-2)), [cc.midolog.gateway.filter.AuthTokenRateLimitFilter](@Order(-1)),
 * cc.midolog.web.filter.RequestIdFilter(@Order(0)), [cc.midolog.gateway.filter.JwtAuthFilter](@Order(1)) 등
 * 모든 보안·식별 필터와 프록시 라우팅이 선행한다. 이 필터는 체인의 가장 마지막(@Order(100))에 위치한다.
 * 앞선 필터에서 거절된 요청(Rate Limit의 429, JWT 검증 실패의 401)이나 프록시 타임아웃(504) 등 조기 종료된
 * 응답까지 빠짐없이 완료 시점(doFinally)에 최종 상태 코드와 소요 시간을 계측하여 저장소에 기록하기 위함이다.
 *
 * 시간 측정 및 Clock 주입:
 * 시스템 시계 대신 [Clock]을 주입받아 요청 시작 시각과 소요 시간을 측정한다. 테스트 환경에서
 * 고정 시계(Clock.fixed)를 주입해 결정적인 시각 검증이 가능하도록 설계되었다.
 */
@Order(100)
@ConditionalOnProperty(prefix = "gateway.request-visibility", name = ["enabled"], havingValue = "true")
class RequestVisibilityFilter(
    private val eventStore: RequestEventStore,
    private val clock: Clock = Clock.systemUTC(),
) : WebFilter {
    /**
     * 요청 시작 시각을 계측하고 응답 완료 시 가시성 이벤트를 기록한다.
     *
     * 체인 완료 콜백(doFinally)에서 HTTP 메서드, 경로, 최종 응답 상태 코드, X-Request-Id,
     * 소요 시간을 추출해 [RequestEventStore]에 추가한다.
     */
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val startedAtMillis = clock.millis()
        return chain.filter(exchange)
            .doFinally {
                eventStore.record(
                    RequestVisibilityEvent(
                        method = exchange.request.method.name(),
                        path = exchange.request.path.pathWithinApplication().value(),
                        status = exchange.response.statusCode?.value(),
                        requestId = exchange.request.headers.getFirst(LoggingMdc.REQUEST_ID),
                        timestamp = Instant.ofEpochMilli(startedAtMillis),
                        durationMs = (clock.millis() - startedAtMillis).coerceAtLeast(0),
                    ),
                )
            }
    }
}
