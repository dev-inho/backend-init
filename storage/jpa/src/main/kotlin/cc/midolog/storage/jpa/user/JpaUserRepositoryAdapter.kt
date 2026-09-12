package cc.midolog.storage.jpa.user

import cc.midolog.user.model.User
import cc.midolog.user.port.repository.UserRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionOperations

/**
 * 사용자 저장소 포트(UserRepositoryPort)의 JPA 기반 영속성 어댑터.
 *
 * jpa 프로파일(@Profile("jpa"))에서 활성화되며, 블로킹 JPA 호출을 Dispatchers.IO 문맥으로 격리해
 * WebFlux 이벤트루프를 막지 않는다.
 * 영속성 조작 및 도메인 변환 처리는 JPA DSL에 의해 빌드 디렉터리에 자동 생성된
 * UserJpaRepository와 UserJpaMapper에 위임한다.
 * save 처리 중 트랜잭션 콜백 실행 결과가 null을 반환하면 저장 또는 매핑 결과가 없는 비정상 상태이므로
 * error("JPA user save transaction returned no result") 예외를 던진다.
 */
@Profile("jpa")
@Repository
class JpaUserRepositoryAdapter(
    private val userJpaRepository: UserJpaRepository,
    private val transactionOperations: TransactionOperations,
) : UserRepositoryPort {

    override suspend fun findById(id: String): User? = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            userJpaRepository.findById(id)
                .map(UserJpaMapper::toDomain)
                .orElse(null)
        }
    }

    override suspend fun save(user: User): User = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            UserJpaMapper.toDomain(
                userJpaRepository.save(UserJpaMapper.toEntity(user)),
            )
        } ?: error("JPA user save transaction returned no result")
    }
}
