package cc.midolog.business.service

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.cache.SampleCachePort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

/** 샘플 비즈니스 서비스 — Redis 캐시 + 코루틴 동시성 예시. */
@Service
class SampleService(
    private val sampleRepositoryPort: SampleRepositoryPort,
    private val sampleCachePort: SampleCachePort,
) {
    /** 캐시 우선 조회(cache-aside): 미스 시 repo 조회 후 캐시에 저장. */
    suspend fun findById(id: String): Sample? {
        sampleCachePort.get(id)?.let { return it }
        val found = sampleRepositoryPort.findById(id) ?: return null
        sampleCachePort.put(found)
        return found
    }

    /** 저장 후 캐시 갱신(write-through). */
    suspend fun save(sample: Sample): Sample {
        val saved = sampleRepositoryPort.save(sample)
        sampleCachePort.put(saved)
        return saved
    }

    /** 두 id를 coroutineScope+async로 동시에 조회. */
    suspend fun findPair(firstId: String, secondId: String): List<Sample> = coroutineScope {
        val a = async { findById(firstId) }
        val b = async { findById(secondId) }
        listOfNotNull(a.await(), b.await())
    }
}
