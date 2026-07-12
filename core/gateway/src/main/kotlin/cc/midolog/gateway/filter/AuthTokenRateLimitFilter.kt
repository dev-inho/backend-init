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
 * 인증 토큰 발급 엔드포인트(POST /api/auth/token) 전용 brute-force 방어 필터.
 * - 대상: POST /api/auth/token 하나뿐이며, 그 외 경로는 그대로 통과한다.
 * - key: 클라이언트 remote address 기준으로 [RateLimiter]를 조회한다.
 * - 한도를 초과하면 429를 반환하고, 응답 본문은 비워 limiter 내부 상태나
 *   자격증명 관련 정보를 노출하지 않는다.
 * - [RateLimiter] 조회 자체가 실패해도 경고 로그 후 요청을 통과시킨다(fail-open).
 *
 * [JwtAuthFilter](@Order(1))보다 먼저 실행되도록 순서를 그보다 낮게 둔다.
 */
@Component
@Order(-1)
class AuthTokenRateLimitFilter(
    private val rateLimiter: RateLimiter,
) : WebFilter {

    private val log = LoggerFactory.getLogger(AuthTokenRateLimitFilter::class.java)

    /** 대상 경로가 아니면 통과, 대상이면 remote address 기준으로 rate limit을 적용한다. */
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
