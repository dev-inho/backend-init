package cc.midolog.gateway.filter

import cc.midolog.gateway.ratelimit.RateLimiter
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 인증 토큰 발급 엔드포인트(POST /api/auth/token) 전용 무차별 대입(brute-force) 방어 필터.
 *
 * 필터 체인 순서 계약:
 * -2 HttpLoggingFilter(support:web) → -1 AuthTokenRateLimitFilter → 0 RequestIdFilter(support:web) → 1 JwtAuthFilter → 100 RequestVisibilityFilter
 *
 * 앞뒤 순서와 위치 이유:
 * 앞에는 최외곽 로깅 필터(cc.midolog.web.filter.HttpLoggingFilter, @Order(-2))가 위치해
 * 모든 인입 요청의 시작과 종료 메타데이터를 기록한다. 뒤에는 cc.midolog.web.filter.RequestIdFilter(@Order(0)),
 * [JwtAuthFilter](@Order(1)), cc.midolog.gateway.visibility.RequestVisibilityFilter(@Order(100))가
 * 실행된다. 토큰 발급 엔드포인트는 로그인 전(미인증 상태)에 호출되므로 [JwtAuthFilter]보다 앞단에서
 * 무차별 대입을 차단해야 한다. 또한 RequestIdFilter보다도 앞서 한도 초과(429)로 즉시 거절함으로써
 * 미인증 공격 트래픽에 대한 불필요한 컨텍스트 생성 비용을 선제적으로 줄인다.
 *
 * 동작 및 정책:
 * - 대상: POST /api/auth/token 하나뿐이며 그 외 경로는 그대로 통과한다.
 * - 키: 클라이언트 remote address 기준으로 [RateLimiter]를 조회한다.
 * - 한도 초과 시 429(TOO_MANY_REQUESTS)를 반환하고 응답 본문은 비워 limiter 내부 상태나 자격증명 정보를 노출하지 않는다.
 * - [RateLimiter] 장애 발생 시 경고 로그 후 요청을 통과시킨다(fail-open).
 */
@Component
@Order(-1)
class AuthTokenRateLimitFilter(
    private val rateLimiter: RateLimiter,
) : WebFilter {

    private val log = LoggerFactory.getLogger(AuthTokenRateLimitFilter::class.java)

    /**
     * 대상 엔드포인트 요청에 대해 클라이언트 IP 기준 rate limit을 적용한다.
     *
     * POST /api/auth/token 경로가 아니면 다음 체인으로 즉시 넘긴다.
     * 대상 요청은 remote address로 생성한 키를 [RateLimiter]에 전달하고,
     * 한도 초과 시 본문 없는 429 응답으로 종료하며 저장소 장애 시 fail-open으로 통과시킨다.
     */
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val request = exchange.request
        if (request.method != HttpMethod.POST || request.path.value() != TARGET_PATH) {
            return chain.filter(exchange)
        }

        val key = "$KEY_PREFIX${resolveClientKey(exchange)}"
        return rateLimiter.tryAcquire(key)
            .onErrorResume { e ->
                log.warn("rate limiter unavailable, failing open: {}", e.message)
                Mono.just(true)
            }
            .flatMap { allowed ->
                if (allowed) chain.filter(exchange) else tooManyRequests(exchange)
            }
    }

    /** 클라이언트를 식별할 rate limit 키(remote address 기준)를 만든다. */
    private fun resolveClientKey(exchange: ServerWebExchange): String =
        exchange.request.remoteAddress?.address?.hostAddress ?: "unknown"

    private fun tooManyRequests(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.TOO_MANY_REQUESTS
        return exchange.response.setComplete()
    }

    companion object {
        private const val TARGET_PATH = "/api/auth/token"
        private const val KEY_PREFIX = "rate-limit:auth-token:"
    }
}
