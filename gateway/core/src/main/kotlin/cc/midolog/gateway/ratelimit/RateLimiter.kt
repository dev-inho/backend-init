package cc.midolog.gateway.ratelimit

import reactor.core.publisher.Mono

/**
 * 요청 허용 여부를 판단하는 rate limiter 계약 인터페이스.
 *
 * 구현체는 지정된 키에 대해 요청 허용 여부를 비동기로 판정한다.
 * 백엔드 저장소(Redis 등) 장애가 발생하더라도 전체 서비스 가용성을 해치지 않도록
 * 스스로 fail-open(true 반환) 정책을 적용하여 예외 대신 허용 신호를 방출해야 한다.
 */
interface RateLimiter {
    /**
     * 지정된 키에 대한 이번 요청의 허용 여부를 판정한다.
     *
     * 한도 이내면 true, 한도 초과 시 false를 방출하는 Mono를 반환한다.
     * 저장소 오류가 발생해도 예외를 전파하지 않고 true를 방출해야 한다.
     */
    fun tryAcquire(key: String): Mono<Boolean>
}
