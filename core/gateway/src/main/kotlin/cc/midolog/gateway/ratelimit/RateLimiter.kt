package cc.midolog.gateway.ratelimit

import reactor.core.publisher.Mono

/**
 * 요청 허용 여부를 판단하는 rate limiter 계약.
 * 구현체는 저장소 장애 시 스스로 fail-open(true 반환) 정책을 적용해야 한다.
 */
interface RateLimiter {
    /** [key]에 대한 이번 요청을 허용하면 true, 한도를 초과했으면 false를 방출한다. */
    fun tryAcquire(key: String): Mono<Boolean>
}
