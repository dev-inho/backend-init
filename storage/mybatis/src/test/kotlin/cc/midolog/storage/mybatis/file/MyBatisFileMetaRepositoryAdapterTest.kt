package cc.midolog.storage.mybatis.file

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.sql.Timestamp
import kotlin.test.Test
import kotlin.test.assertEquals

class MyBatisFileMetaRepositoryAdapterTest {

    @Test
    fun `findById handles various timestamp types correctly`() = runBlocking {
        val instant = Instant.ofEpochMilli(1700000000000L)
        val sqlTimestamp = Timestamp.from(instant)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.of("UTC"))

        // test Instant
        var mapper = FakeFileMetaMapper(row = mapOf("id" to "f1", "ownerId" to "o1", "storageKey" to "k1", "status" to "READY", "createdAt" to instant, "updatedAt" to instant))
        var adapter = MyBatisFileMetaRepositoryAdapter(mapper)
        var result = adapter.findById("f1")!!
        assertEquals(instant, result.createdAt)

        // test Timestamp
        mapper = FakeFileMetaMapper(row = mapOf("id" to "f1", "ownerId" to "o1", "storageKey" to "k1", "status" to "READY", "createdAt" to sqlTimestamp, "updatedAt" to sqlTimestamp))
        adapter = MyBatisFileMetaRepositoryAdapter(mapper)
        result = adapter.findById("f1")!!
        assertEquals(instant, result.createdAt)

        // test LocalDateTime
        mapper = FakeFileMetaMapper(row = mapOf("id" to "f1", "ownerId" to "o1", "storageKey" to "k1", "status" to "READY", "createdAt" to localDateTime, "updatedAt" to localDateTime))
        adapter = MyBatisFileMetaRepositoryAdapter(mapper)
        result = adapter.findById("f1")!!
        assertEquals(instant, result.createdAt)
    }

    private class FakeFileMetaMapper(private val row: Map<String, Any?>? = null) : FileMetaMapper {
        override fun selectById(id: String): Map<String, Any?>? = row
        override fun upsert(id: String, ownerId: String, storageKey: String, sizeBytes: Long?, contentType: String?, checksum: String?, status: String, createdAt: Instant, updatedAt: Instant): Int = 1
        override fun updateStatus(id: String, status: String, updatedAt: Instant): Int = 1
        override fun findExpiredPending(status: String, cutoff: Instant, limit: Int): List<Map<String, Any?>> = emptyList()
    }
}
