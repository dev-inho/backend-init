package cc.midolog.sample.port.cache

import cc.midolog.sample.model.Sample

/**
 * 샘플 도메인 캐시 조작을 추상화하는 출력 포트.
 *
 * 인프라 캐시 저장소(Redis 등)에 대한 직접적인 의존성을 격리하여 도메인 순수성을 유지한다.
 * 구현체는 RedisSampleCacheAdapter 등이 담당한다.
 */
interface SampleCachePort {
    /**
     * 식별자로 캐시된 샘플을 조회한다.
     * 캐시 미스가 발생하거나 데이터 파싱에 실패하면 null을 반환한다.
     */
    suspend fun get(id: String): Sample?

    /**
     * 샘플 데이터를 캐시에 저장한다.
     * 캐시 엔트리는 구현체 정책에 따른 유효 기간(기본 10분 TTL) 동안 유지된다.
     */
    suspend fun put(sample: Sample)
}
