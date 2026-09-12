package cc.midolog.business.service

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.StoredFile
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.FileStoragePort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class FileOrphanCleanupServiceTest {

    @Test
    fun `만료된 PENDING만 FAILED로 갱신되며, 예외 시 무시하고 다음 것을 처리하고, 무한 루프에 빠지지 않는다`() = runBlocking {
        val now = Instant.parse("2026-09-12T10:00:00Z")
        val clock = Clock.fixed(now, ZoneId.of("UTC"))
        val pendingTtl = Duration.ofHours(1)
        val cutoff = now.minus(pendingTtl) // 09:00:00Z

        val meta1 = FileMeta("id1", "o1", "key1", 100L, "text/plain", "hash", FileStatus.PENDING, Instant.parse("2026-09-12T08:00:00Z"), Instant.parse("2026-09-12T08:00:00Z")) // Expired
        val meta2 = FileMeta("id2", "o2", "key2", 100L, "text/plain", "hash", FileStatus.PENDING, Instant.parse("2026-09-12T08:30:00Z"), Instant.parse("2026-09-12T08:30:00Z")) // Expired
        val meta3 = FileMeta("id3", "o3", "key3", 100L, "text/plain", "hash", FileStatus.PENDING, Instant.parse("2026-09-12T09:30:00Z"), Instant.parse("2026-09-12T09:30:00Z")) // Not expired
        val meta4 = FileMeta("id4", "o4", "key4", 100L, "text/plain", "hash", FileStatus.PENDING, Instant.parse("2026-09-12T08:45:00Z"), Instant.parse("2026-09-12T08:45:00Z")) // Expired, will throw exception

        val db = mutableMapOf(
            "id1" to meta1,
            "id2" to meta2,
            "id3" to meta3,
            "id4" to meta4
        )
        
        val deletedKeys = mutableListOf<String>()

        val storagePort = object : FileStoragePort {
            override suspend fun store(key: String, reader: ChunkReader, knownSize: Long?, contentType: String, expectedChecksum: String?): StoredFile = throw NotImplementedError()
            override suspend fun load(key: String): ChunkReader? = null
            override suspend fun delete(key: String): Boolean {
                if (key == "key4") throw RuntimeException("Simulated exception")
                deletedKeys.add(key)
                return true
            }
            override suspend fun exists(key: String): Boolean = false
        }

        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = db[id]
            override suspend fun save(file: FileMeta): FileMeta = file
            override suspend fun updateStatus(id: String, status: FileStatus): Boolean {
                val f = db[id]
                if (f != null) {
                    db[id] = f.copy(status = status)
                    return true
                }
                return false
            }
            override suspend fun findExpiredPending(cutoffTime: Instant, limit: Int): List<FileMeta> {
                return db.values.filter { it.status == FileStatus.PENDING && it.updatedAt.isBefore(cutoffTime) }
                    .sortedBy { it.updatedAt }
                    .take(limit)
            }
        }

        val service = FileOrphanCleanupService(clock, repoPort, storagePort)

        service.cleanup(pendingTtl, batchSize = 2)

        // meta1, meta2 should be FAILED.
        assertEquals(FileStatus.FAILED, db["id1"]?.status)
        assertEquals(FileStatus.FAILED, db["id2"]?.status)
        
        // meta3 should remain PENDING (not expired)
        assertEquals(FileStatus.PENDING, db["id3"]?.status)

        // meta4 should remain PENDING due to exception, but shouldn't cause infinite loop.
        assertEquals(FileStatus.PENDING, db["id4"]?.status)

        // storage delete was called for id1, id2
        assertTrue(deletedKeys.contains("key1"))
        assertTrue(deletedKeys.contains("key2"))
        
        // batch limit is 2.
        // It fetches [id1, id2] - both succeed.
        // Next fetch: [id4] - exception.
        // Next fetch: [id4] - stops because id4 is in failedIds.
    }
}
