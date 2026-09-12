package cc.midolog.sample.port.cache

import cc.midolog.sample.model.Sample

/**
 * 샘플 도메인 캐시 포트.
 *
 * 외부 인프라(Redis 등)에 대한 캐시 동작을 추상화하여 도메인이 인프라를 직접 모르게 한다.
 */
interface SampleCachePort {
    suspend fun get(id: String): Sample?
    suspend fun put(sample: Sample)
}
