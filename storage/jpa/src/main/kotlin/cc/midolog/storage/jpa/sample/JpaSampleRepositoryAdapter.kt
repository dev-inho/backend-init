package cc.midolog.storage.jpa.sample

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionOperations

/**
 * 샘플 저장소 포트(SampleRepositoryPort)의 JPA 기반 영속성 어댑터.
 *
 * jpa 프로파일(@Profile("jpa"))에서 활성화되며, 블로킹 JPA 호출을 Dispatchers.IO 문맥으로 격리해
 * WebFlux 이벤트루프를 막지 않는다.
 * 영속성 조작 및 도메인 변환 처리는 JPA DSL에 의해 빌드 디렉터리에 자동 생성된
 * SampleJpaRepository와 SampleJpaMapper에 위임한다.
 * save 처리 중 트랜잭션 콜백 실행 결과가 null을 반환하면 데이터 저장 또는 매핑이 실패한 것으로 간주하여
 * error("JPA sample save transaction returned no result") 예외를 던진다.
 */
@Profile("jpa")
@Repository
class JpaSampleRepositoryAdapter(
    private val sampleJpaRepository: SampleJpaRepository,
    private val transactionOperations: TransactionOperations,
) : SampleRepositoryPort {

    override suspend fun findById(id: String): Sample? = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            sampleJpaRepository.findById(id)
                .map(SampleJpaMapper::toDomain)
                .orElse(null)
        }
    }

    override suspend fun save(sample: Sample): Sample = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            SampleJpaMapper.toDomain(
                sampleJpaRepository.save(SampleJpaMapper.toEntity(sample)),
            )
        } ?: error("JPA sample save transaction returned no result")
    }
}
