package cc.midolog.business.service

import cc.midolog.sample.model.Sample
import cc.midolog.sample.policy.SampleSavePolicy
import cc.midolog.sample.port.cache.SampleCachePort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Service

/**
 * 캐시 및 리포지토리를 결합한 샘플 비즈니스 서비스.
 *
 * 외부 인프라스트럭처를 직접 알지 못하도록 도메인 포트([SampleRepositoryPort], [SampleCachePort])에만 의존하며,
 * 코루틴 기반의 비동기 처리 및 캐시 연동 패턴을 제시한다.
 */
@Service
class SampleService(
    private val sampleRepositoryPort: SampleRepositoryPort,
    private val sampleCachePort: SampleCachePort,
    private val sampleSavePolicies: List<SampleSavePolicy> = emptyList(),
) {
    /**
     * 식별자로 샘플 엔티티를 캐시 우선(cache-aside) 방식으로 조회한다.
     *
     * 캐시 저장소에 키가 존재하면 즉시 반환하고, 미스 발생 시 영속성 리포지토리를 조회한 뒤 결과를 캐시에 적재한다. 일치하는 데이터가 없으면 null을 반환한다.
     */
    suspend fun findById(id: String): Sample? {
        sampleCachePort.get(id)?.let { return it }
        val found = sampleRepositoryPort.findById(id) ?: return null
        sampleCachePort.put(found)
        return found
    }

    /**
     * 샘플 엔티티에 등록된 [SampleSavePolicy] 정책들을 [SampleSavePolicy.order] 오름차순으로 적용한 후
     * 영속 저장소에 저장하고 캐시를 갱신(write-through)한다.
     *
     * 저장소에 데이터를 성공적으로 반영한 직후 캐시 포트에도 동일 객체를 적재하여 최신 상태를 유지한다.
     */
    suspend fun save(sample: Sample): Sample {
        val processed = sampleSavePolicies
            .sortedBy { it.order }
            .fold(sample) { acc, policy -> policy.beforeSave(acc) }
        val saved = sampleRepositoryPort.save(processed)
        sampleCachePort.put(saved)
        return saved
    }

    /**
     * 두 식별자의 샘플 데이터를 코루틴을 통해 병렬로 동시 조회하는 예시 메서드.
     *
     * [coroutineScope] 내에서 두 개의 [async] 블록을 시작하여 I/O 지연을 중첩 실행한다.
     * 실제 호출 소비자가 없어 docs/DEAD_CODE_CANDIDATES.md #3에 삭제 후보로 등재되어 있으나 동시성 패턴 가이드 목적으로 보존한다.
     */
    suspend fun findPair(firstId: String, secondId: String): List<Sample> = coroutineScope {
        val a = async { findById(firstId) }
        val b = async { findById(secondId) }
        listOfNotNull(a.await(), b.await())
    }
}
