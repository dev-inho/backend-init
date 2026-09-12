package cc.midolog.storage.jpa.file

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import jakarta.persistence.EntityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import org.springframework.transaction.support.TransactionOperations

@Profile("jpa")
@Repository
class JpaFileMetaRepositoryAdapter(
    private val fileMetaJpaRepository: FileMetaJpaRepository,
    private val transactionOperations: TransactionOperations,
    private val entityManager: EntityManager,
    private val clock: java.time.Clock = java.time.Clock.systemUTC(),
) : FileMetaRepositoryPort {

    override suspend fun findById(id: String): FileMeta? = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            fileMetaJpaRepository.findById(id)
                .map(FileMetaJpaMapper::toDomain)
                .orElse(null)
        }
    }

    override suspend fun save(file: FileMeta): FileMeta = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            FileMetaJpaMapper.toDomain(
                fileMetaJpaRepository.save(FileMetaJpaMapper.toEntity(file)),
            )
        } ?: error("JPA file_meta save transaction returned no result")
    }

    override suspend fun updateStatus(id: String, status: FileStatus): Boolean = withContext(Dispatchers.IO) {
        transactionOperations.execute {
            val query = entityManager.createQuery(
                "UPDATE FileMetaJpaEntity e SET e.status = :status, e.updatedAt = :now WHERE e.id = :id"
            )
            query.setParameter("status", status)
            query.setParameter("now", clock.instant())
            query.setParameter("id", id)
            query.executeUpdate() > 0
        } ?: false
    }

    override suspend fun findExpiredPending(cutoff: java.time.Instant, limit: Int): List<FileMeta> = withContext(Dispatchers.IO) {
        require(limit > 0) { "limit must be positive" }
        transactionOperations.execute {
            val query = entityManager.createQuery(
                "SELECT e FROM FileMetaJpaEntity e WHERE e.status = :status AND e.updatedAt < :cutoff ORDER BY e.updatedAt ASC",
                FileMetaJpaEntity::class.java
            )
            query.setParameter("status", FileStatus.PENDING)
            query.setParameter("cutoff", cutoff)
            query.maxResults = limit

            query.resultList.map(FileMetaJpaMapper::toDomain)
        } ?: emptyList()
    }
}
