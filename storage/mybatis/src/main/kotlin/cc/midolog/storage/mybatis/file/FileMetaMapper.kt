package cc.midolog.storage.mybatis.file

import cc.midolog.file.model.FileStatus
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.Instant

@Mapper
interface FileMetaMapper {
    fun selectById(@Param("id") id: String): Map<String, Any?>?

    fun upsert(
        @Param("id") id: String,
        @Param("ownerId") ownerId: String,
        @Param("storageKey") storageKey: String,
        @Param("sizeBytes") sizeBytes: Long?,
        @Param("contentType") contentType: String?,
        @Param("checksum") checksum: String?,
        @Param("status") status: String,
        @Param("createdAt") createdAt: Instant,
        @Param("updatedAt") updatedAt: Instant
    ): Int

    fun updateStatus(
        @Param("id") id: String,
        @Param("status") status: String,
        @Param("updatedAt") updatedAt: Instant
    ): Int

    fun findExpiredPending(
        @Param("status") status: String,
        @Param("cutoff") cutoff: Instant,
        @Param("limit") limit: Int
    ): List<Map<String, Any?>>
}
