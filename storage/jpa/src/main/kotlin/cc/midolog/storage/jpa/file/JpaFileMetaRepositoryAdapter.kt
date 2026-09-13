package cc.midolog.storage.jpa.file

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.transaction.support.TransactionOperations

class JpaFileMetaRepositoryAdapter(
    private val fileMetaJpaRepository: FileMetaJpaRepository,
    private val transactionOperations: TransactionOperations,
    private val jpaQueryFactory: com.querydsl.jpa.impl.JPAQueryFactory,
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
            val q = cc.midolog.storage.jpa.file.QFileMetaJpaEntity.fileMetaJpaEntity
            jpaQueryFactory.update(q)
                .set(q.status, status)
                .set(q.updatedAt, clock.instant())
                .where(q.id.eq(id))
                .execute() > 0
        } ?: false
    }

    override suspend fun findExpiredPending(cutoff: java.time.Instant, limit: Int): List<FileMeta> = withContext(Dispatchers.IO) {
        require(limit > 0) { "limit must be positive" }
        transactionOperations.execute {
            val q = cc.midolog.storage.jpa.file.QFileMetaJpaEntity.fileMetaJpaEntity
            jpaQueryFactory.selectFrom(q)
                .where(
                    q.status.eq(FileStatus.PENDING),
                    q.updatedAt.lt(cutoff)
                )
                .orderBy(q.updatedAt.asc())
                .limit(limit.toLong())
                .fetch()
                .map(FileMetaJpaMapper::toDomain)
        } ?: emptyList()
    }
}
