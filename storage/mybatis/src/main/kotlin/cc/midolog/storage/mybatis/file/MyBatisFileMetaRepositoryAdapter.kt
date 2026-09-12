package cc.midolog.storage.mybatis.file

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.time.Instant

@Profile("mybatis")
@Repository
class MyBatisFileMetaRepositoryAdapter(
    private val mapper: FileMetaMapper,
) : FileMetaRepositoryPort {

    override suspend fun findById(id: String): FileMeta? {
        val row = mapper.selectById(id) ?: return null
        return toDomain(row)
    }

    override suspend fun save(file: FileMeta): FileMeta {
        mapper.upsert(
            id = file.id,
            ownerId = file.ownerId,
            storageKey = file.storageKey,
            sizeBytes = file.sizeBytes,
            contentType = file.contentType,
            checksum = file.checksum,
            status = file.status.name,
            createdAt = file.createdAt,
            updatedAt = file.updatedAt
        )
        return file
    }

    override suspend fun updateStatus(id: String, status: FileStatus): Boolean {
        return mapper.updateStatus(id, status.name, Instant.now()) > 0
    }

    override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> {
        return mapper.findExpiredPending(FileStatus.PENDING.name, cutoff, limit)
            .map { toDomain(it) }
    }

    private fun toDomain(row: Map<String, Any?>): FileMeta {
        return FileMeta(
            id = row["id"] as String,
            ownerId = row["ownerId"] as String,
            storageKey = row["storageKey"] as String,
            sizeBytes = (row["sizeBytes"] as? Number)?.toLong(),
            contentType = row["contentType"] as? String,
            checksum = row["checksum"] as? String,
            status = FileStatus.valueOf(row["status"] as String),
            createdAt = mapInstant(row["createdAt"]),
            updatedAt = mapInstant(row["updatedAt"])
        )
    }

    private fun mapInstant(value: Any?): Instant {
        return when (value) {
            is Instant -> value
            is java.sql.Timestamp -> value.toInstant()
            is java.time.LocalDateTime -> value.atZone(java.time.ZoneId.systemDefault()).toInstant()
            else -> error("Cannot map timestamp $value to Instant")
        }
    }
}
