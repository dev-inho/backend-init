package cc.midolog.gateway.ratelimit

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.data.redis.core.script.RedisScript
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

/**
 * Redis Lua 스크립트 기반 고정 윈도우 rate limiter.
 *
 * INCR와 첫 증가 시 EXPIRE 설정을 하나의 Lua 스크립트로 묶어 원자적으로
 * 실행한다 — INCR와 EXPIRE 사이에서 장애가 나도 TTL 없는 키가 남지 않는다.
 * Redis 오류가 발생하면 경고 로그를 남기고 요청을 통과시킨다(fail-open) —
 * 인증 자체는 JwtAuthFilter/AuthController가 별도로 담당하므로 이 필터의
 * 장애가 보안 통제 상실로 이어지지 않는다.
 */
@Component
class RedisRateLimiter(
    private val redisTemplate: ReactiveStringRedisTemplate,
    @Value("\${gateway.rate-limit.auth-token.limit:10}") private val limit: Long,
    @Value("\${gateway.rate-limit.auth-token.window-seconds:60}") private val windowSeconds: Long,
) : RateLimiter {

    private val log = LoggerFactory.getLogger(RedisRateLimiter::class.java)

    /** INCR + 첫 증가 시 EXPIRE를 원자적으로 수행하고 현재 카운트를 반환하는 스크립트. */
    private val incrementScript: RedisScript<Long> = RedisScript.of(
        """
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
        end
        return count
        """.trimIndent(),
        Long::class.java,
    )

    /**
     * Redis 카운터를 원자적으로 증가시키고 허용 한도 이내인지 판정한다.
     *
     * Lua 스크립트를 실행해 카운트를 증가시키고, 현재 값이 limit 이하이면 true,
     * 초과하면 false를 방출한다. Redis 명령 실패나 타임아웃 등 예외가 발생하면
     * 경고 로그를 기록한 뒤 fail-open 정책에 따라 true를 반환한다.
     */
    override fun tryAcquire(key: String): Mono<Boolean> =
        redisTemplate.execute(incrementScript, listOf(key), listOf(windowSeconds.toString()))
            .next()
            .map { count -> count <= limit }
            .defaultIfEmpty(true)
            .onErrorResume { e ->
                log.warn("rate limit check failed for key, failing open: {}", e.message)
                Mono.just(true)
            }
}
