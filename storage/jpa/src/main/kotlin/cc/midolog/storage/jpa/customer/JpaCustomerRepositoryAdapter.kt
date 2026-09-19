package cc.midolog.storage.jpa.customer

import cc.midolog.customer.port.repository.CustomerRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.transaction.support.TransactionOperations

/**
 * 고객 확장 엔티티 영속성을 위한 JPA 기반 범용 저장소 어댑터.
 *
 * Spring Data JpaRepository와 도메인-엔티티 양방향 변환 함수를 주입받아
 * CustomerRepositoryPort 계약을 충족한다.
 * 블로킹 JDBC 호출은 Dispatchers.IO 문맥으로 격리하여 WebFlux 이벤트루프 차단을 방지하며,
 * 트랜잭션 템플릿 내에서 단건 조회, upsert 저장, 삭제 연산을 원자적으로 수행한다.
 */
class JpaCustomerRepositoryAdapter<T : Any, ID : Any, E : Any>(
    private val jpaRepository: JpaRepository<E, ID>,
    private val transactionOperations: TransactionOperations,
    private val toDomain: (E) -> T,
    private val toEntity: (T) -> E,
    private val idOfDomain: (T) -> ID,
) : CustomerRepositoryPort<T, ID> {

    override suspend fun findById(id: ID): T? = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            jpaRepository.findById(id)
                .map(toDomain)
                .orElse(null)
        }
    }

    override suspend fun save(entity: T): T = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            val jpaEntity = toEntity(entity)
            val savedEntity = jpaRepository.save(jpaEntity)
            toDomain(savedEntity)
        } ?: error("JPA customer entity save transaction returned no result for id: ${idOfDomain(entity)}")
    }

    override suspend fun deleteById(id: ID): Boolean = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            if (jpaRepository.existsById(id)) {
                jpaRepository.deleteById(id)
                true
            } else {
                false
            }
        } ?: false
    }
}
