package cc.midolog.storage.mybatis.file

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.storage.mybatis.file.FileMetaDynamicSqlSupport.fileMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.select
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.update
import java.time.Clock
import java.time.Instant

/**
 * 파일 메타데이터 저장소 포트(FileMetaRepositoryPort)의 MyBatis 기반 영속성 어댑터.
 *
 * 블로킹 JDBC 호출을 Dispatchers.IO로 격리하여 WebFlux 이벤트루프를 차단하지 않는다.
 * 조회 및 상태 갱신은 도메인 DSL로 자동 생성된 FileMetaDynamicSqlSupport를 통한 MyBatis Dynamic SQL로 수행하며,
 * Upsert 동작은 PostgreSQL과 H2 방언 분기(databaseId)를 위해 XML 조각으로 위임하고 affectedRows == 1을 검증한다.
 */
class MyBatisFileMetaRepositoryAdapter(
    private val mapper: FileMetaMapper,
    private val clock: Clock = Clock.systemUTC(),
) : FileMetaRepositoryPort {

    override suspend fun findById(id: String): FileMeta? = withContext(Dispatchers.IO) {
        val selectStatement = select(
            FileMetaDynamicSqlSupport.id,
            FileMetaDynamicSqlSupport.ownerId,
            FileMetaDynamicSqlSupport.storageKey,
            FileMetaDynamicSqlSupport.sizeBytes,
            FileMetaDynamicSqlSupport.contentType,
            FileMetaDynamicSqlSupport.checksum,
            FileMetaDynamicSqlSupport.status,
            FileMetaDynamicSqlSupport.createdAt,
            FileMetaDynamicSqlSupport.updatedAt,
        ) {
            from(fileMeta)
            where { FileMetaDynamicSqlSupport.id isEqualTo id }
        }
        val row = mapper.selectOneMappedRow(selectStatement) ?: return@withContext null
        toDomain(row)
    }

    override suspend fun save(file: FileMeta): FileMeta = withContext(Dispatchers.IO) {
        val affected = mapper.upsert(
            id = file.id,
            ownerId = file.ownerId,
            storageKey = file.storageKey,
            sizeBytes = file.sizeBytes,
            contentType = file.contentType,
            checksum = file.checksum,
            status = file.status.name,
            createdAt = file.createdAt,
            updatedAt = file.updatedAt,
        )
        check(affected == 1) { "Save failed, affected rows: $affected" }
        file
    }

    override suspend fun updateStatus(id: String, status: FileStatus): Boolean = withContext(Dispatchers.IO) {
        val now = clock.instant()
        val updateStatement = update(fileMeta) {
            set(FileMetaDynamicSqlSupport.status).equalTo(status)
            set(FileMetaDynamicSqlSupport.updatedAt).equalTo(now)
            where { FileMetaDynamicSqlSupport.id isEqualTo id }
        }
        mapper.update(updateStatement) > 0
    }

    override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> = withContext(Dispatchers.IO) {
        require(limit > 0) { "limit must be positive" }
        val selectStatement = select(
            FileMetaDynamicSqlSupport.id,
            FileMetaDynamicSqlSupport.ownerId,
            FileMetaDynamicSqlSupport.storageKey,
            FileMetaDynamicSqlSupport.sizeBytes,
            FileMetaDynamicSqlSupport.contentType,
            FileMetaDynamicSqlSupport.checksum,
            FileMetaDynamicSqlSupport.status,
            FileMetaDynamicSqlSupport.createdAt,
            FileMetaDynamicSqlSupport.updatedAt,
        ) {
            from(fileMeta)
            where {
                FileMetaDynamicSqlSupport.status isEqualTo FileStatus.PENDING
                and { FileMetaDynamicSqlSupport.updatedAt isLessThan cutoff }
            }
            orderBy(FileMetaDynamicSqlSupport.updatedAt)
            limit(limit.toLong())
        }
        mapper.selectManyMappedRows(selectStatement).map { toDomain(it) }
    }

    private fun toDomain(row: Map<String, Any?>): FileMeta {
        val rawStatus = row["status"] ?: row["STATUS"]
        val status = when (rawStatus) {
            is FileStatus -> rawStatus
            is String -> FileStatus.valueOf(rawStatus)
            else -> error("Cannot map status $rawStatus to FileStatus")
        }
        return FileMeta(
            id = (row["id"] ?: row["ID"]) as String,
            ownerId = (row["owner_id"] ?: row["ownerId"] ?: row["OWNER_ID"]) as String,
            storageKey = (row["storage_key"] ?: row["storageKey"] ?: row["STORAGE_KEY"]) as String,
            sizeBytes = ((row["size_bytes"] ?: row["sizeBytes"] ?: row["SIZE_BYTES"]) as? Number)?.toLong(),
            contentType = (row["content_type"] ?: row["contentType"] ?: row["CONTENT_TYPE"]) as? String,
            checksum = (row["checksum"] ?: row["CHECKSUM"]) as? String,
            status = status,
            createdAt = mapInstant(row["created_at"] ?: row["createdAt"] ?: row["CREATED_AT"]),
            updatedAt = mapInstant(row["updated_at"] ?: row["updatedAt"] ?: row["UPDATED_AT"]),
        )
    }

    private fun mapInstant(value: Any?): Instant {
        return when (value) {
            is Instant -> value
            is java.sql.Timestamp -> value.toInstant()
            is java.time.LocalDateTime -> value.atZone(java.time.ZoneId.of("UTC")).toInstant()
            else -> error("Cannot map timestamp $value to Instant")
        }
    }
}
