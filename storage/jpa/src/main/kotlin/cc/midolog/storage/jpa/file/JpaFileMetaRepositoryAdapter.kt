package cc.midolog.storage.jpa.file

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.transaction.support.TransactionOperations
import java.time.Clock
import java.time.Instant

/**
 * JPA 및 QueryDSL을 활용한 파일 메타데이터 저장소 어댑터.
 *
 * 블로킹 DB I/O를 Dispatchers.IO로 격리하여 WebFlux 이벤트루프를 차단하지 않으며,
 * 조건부 상태 전이를 통해 동시성 경합 시 상태 정합성을 보장한다.
 */
class JpaFileMetaRepositoryAdapter(
    private val fileMetaJpaRepository: FileMetaJpaRepository,
    private val transactionOperations: TransactionOperations,
    private val jpaQueryFactory: com.querydsl.jpa.impl.JPAQueryFactory,
    private val clock: Clock = Clock.systemUTC(),
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

    override suspend fun updateStatusConditionally(
        id: String,
        expectedStatuses: Set<FileStatus>,
        newStatus: FileStatus,
        sizeBytes: Long?,
        contentType: String?,
        checksum: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        if (expectedStatuses.isEmpty()) return@withContext false
        transactionOperations.execute {
            val q = cc.midolog.storage.jpa.file.QFileMetaJpaEntity.fileMetaJpaEntity
            val updateClause = jpaQueryFactory.update(q)
                .set(q.status, newStatus)
                .set(q.updatedAt, clock.instant())

            if (sizeBytes != null) updateClause.set(q.sizeBytes, sizeBytes)
            if (contentType != null) updateClause.set(q.contentType, contentType)
            if (checksum != null) updateClause.set(q.checksum, checksum)

            updateClause
                .where(
                    q.id.eq(id),
                    q.status.`in`(expectedStatuses),
                )
                .execute() > 0
        } ?: false
    }

    override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> =
        findExpiredOrphans(cutoff, limit, setOf(FileStatus.PENDING))

    override suspend fun findExpiredOrphans(
        cutoff: Instant,
        limit: Int,
        statuses: Set<FileStatus>,
    ): List<FileMeta> = withContext(Dispatchers.IO) {
        require(limit > 0) { "limit must be positive" }
        if (statuses.isEmpty()) return@withContext emptyList()
        transactionOperations.execute {
            val q = cc.midolog.storage.jpa.file.QFileMetaJpaEntity.fileMetaJpaEntity
            jpaQueryFactory.selectFrom(q)
                .where(
                    q.status.`in`(statuses),
                    q.updatedAt.lt(cutoff),
                )
                .orderBy(q.updatedAt.asc())
                .limit(limit.toLong())
                .fetch()
                .map(FileMetaJpaMapper::toDomain)
        } ?: emptyList()
    }
}
