package cc.midolog.storage.mybatis.file

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Proxy
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class MyBatisFileMetaRepositoryAdapterTest {

    @Test
    fun `findById handles various timestamp types correctly`() = runBlocking {
        val instant = Instant.ofEpochMilli(1700000000000L)
        val sqlTimestamp = Timestamp.from(instant)
        val localDateTime = LocalDateTime.ofInstant(instant, ZoneId.of("UTC"))

        // test Instant
        var mapper = fakeFileMetaMapper(
            row = mapOf(
                "id" to "f1",
                "owner_id" to "o1",
                "storage_key" to "k1",
                "status" to "READY",
                "created_at" to instant,
                "updated_at" to instant,
            ),
        )
        var adapter = MyBatisFileMetaRepositoryAdapter(mapper)
        var result = adapter.findById("f1")!!
        assertEquals(instant, result.createdAt)

        // test Timestamp
        mapper = fakeFileMetaMapper(
            row = mapOf(
                "id" to "f1",
                "owner_id" to "o1",
                "storage_key" to "k1",
                "status" to "READY",
                "created_at" to sqlTimestamp,
                "updated_at" to sqlTimestamp,
            ),
        )
        adapter = MyBatisFileMetaRepositoryAdapter(mapper)
        result = adapter.findById("f1")!!
        assertEquals(instant, result.createdAt)

        // test LocalDateTime
        mapper = fakeFileMetaMapper(
            row = mapOf(
                "id" to "f1",
                "owner_id" to "o1",
                "storage_key" to "k1",
                "status" to "READY",
                "created_at" to localDateTime,
                "updated_at" to localDateTime,
            ),
        )
        adapter = MyBatisFileMetaRepositoryAdapter(mapper)
        result = adapter.findById("f1")!!
        assertEquals(instant, result.createdAt)
    }

    @Test
    fun `save fails if affected rows is 0 or 2`() = runBlocking {
        val instant = Instant.ofEpochMilli(1700000000000L)
        val file = FileMeta("f1", "o1", "k1", null, null, null, FileStatus.PENDING, instant, instant)

        val adapter1 = MyBatisFileMetaRepositoryAdapter(fakeFileMetaMapper(upsertResult = 0))
        val exception1 = kotlin.test.assertFailsWith<IllegalStateException> { adapter1.save(file) }
        assertEquals("Save failed, affected rows: 0", exception1.message)

        val adapter2 = MyBatisFileMetaRepositoryAdapter(fakeFileMetaMapper(upsertResult = 2))
        val exception2 = kotlin.test.assertFailsWith<IllegalStateException> { adapter2.save(file) }
        assertEquals("Save failed, affected rows: 2", exception2.message)
    }

    private fun fakeFileMetaMapper(
        row: Map<String, Any?>? = null,
        rows: List<Map<String, Any?>> = emptyList(),
        upsertResult: Int = 1,
        updateResult: Int = 1,
    ): FileMetaMapper {
        return Proxy.newProxyInstance(
            FileMetaMapper::class.java.classLoader,
            arrayOf(FileMetaMapper::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "selectOneMappedRow" -> row
                "selectManyMappedRows" -> rows
                "update" -> updateResult
                "upsert" -> upsertResult
                "toString" -> "FakeFileMetaMapper"
                "hashCode" -> 1
                "equals" -> false
                else -> null
            }
        } as FileMetaMapper
    }
}
