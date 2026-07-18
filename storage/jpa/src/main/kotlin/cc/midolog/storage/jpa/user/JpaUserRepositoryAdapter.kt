package cc.midolog.storage.jpa.user

import cc.midolog.user.model.User
import cc.midolog.user.port.repository.UserRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionOperations

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
