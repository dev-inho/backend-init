package cc.midolog.business.service

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.StoredFile
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.FileStoragePort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class FileOrphanCleanupServiceTest {

    private val now = Instant.parse("2026-09-12T10:00:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("UTC"))
    private val pendingTtl = Duration.ofHours(1)
    private val cutoff = now.minus(pendingTtl) // 09:00:00Z

    private lateinit var db: MutableMap<String, FileMeta>
    private lateinit var deletedKeys: MutableList<String>
    private lateinit var calledLimits: MutableList<Int>
    private lateinit var storagePort: FakeFileStoragePort
    private lateinit var repoPort: FakeFileMetaRepositoryPort
    private lateinit var service: FileOrphanCleanupService

    @BeforeEach
    fun setUp() {
        db = mutableMapOf()
        deletedKeys = mutableListOf()
        calledLimits = mutableListOf()
        storagePort = FakeFileStoragePort(deletedKeys)
        repoPort = FakeFileMetaRepositoryPort(db, calledLimits)
        service = FileOrphanCleanupService(clock, repoPort, storagePort)
    }

    @Test
    fun `TTL을 지난 PENDING 건만 정리한다`() = runBlocking {
        addPending("id1", "2026-09-12T08:00:00Z") // Expired
        addPending("id2", "2026-09-12T09:30:00Z") // Not Expired

        service.cleanup(pendingTtl, batchSize = 10)

        assertEquals(FileStatus.FAILED, db["id1"]?.status)
        assertEquals(FileStatus.PENDING, db["id2"]?.status)
        assertTrue(deletedKeys.contains("key-id1"))
        assertFalse(deletedKeys.contains("key-id2"))
    }

    @Test
    fun `batchSize 2에서 3건을 2개 배치로 나누어 처리한다`() = runBlocking {
        addPending("id1", "2026-09-12T08:00:00Z")
        addPending("id2", "2026-09-12T08:10:00Z")
        addPending("id3", "2026-09-12T08:20:00Z")

        service.cleanup(pendingTtl, batchSize = 2)

        assertEquals(FileStatus.FAILED, db["id1"]?.status)
        assertEquals(FileStatus.FAILED, db["id2"]?.status)
        assertEquals(FileStatus.FAILED, db["id3"]?.status)

        // 2 batches: first fetch 2, second fetch 2 (gets 1 and breaks because 1 < 2)
        assertEquals(listOf(2, 2), calledLimits)
    }

    @Test
    fun `storage 예외 시 예외가 발생한 항목의 상태는 불변이며 다음 항목 처리는 계속된다`() = runBlocking {
        addPending("id1", "2026-09-12T08:00:00Z")
        addPending("id2", "2026-09-12T08:10:00Z")

        storagePort.throwOnKey = "key-id1"

        service.cleanup(pendingTtl, batchSize = 10)

        assertEquals(FileStatus.PENDING, db["id1"]?.status) // Immutable on error
        assertEquals(FileStatus.FAILED, db["id2"]?.status)  // Next item processed successfully

        assertFalse(deletedKeys.contains("key-id1"))
        assertTrue(deletedKeys.contains("key-id2"))
    }

    @Test
    fun `delete가 false를 반환하면 메타는 불변이다`() = runBlocking {
        addPending("id1", "2026-09-12T08:00:00Z")

        storagePort.returnFalseOnKey = "key-id1"

        service.cleanup(pendingTtl, batchSize = 10)

        assertEquals(FileStatus.PENDING, db["id1"]?.status) // Immutable when returned false
        assertFalse(deletedKeys.contains("key-id1"))
    }

    @Test
    fun `선두 2건이 실패해도 3번째 만료 건이 기아 없이 성공한다 (batchSize=2)`() = runBlocking {
        addPending("id1", "2026-09-12T08:00:00Z")
        addPending("id2", "2026-09-12T08:10:00Z")
        addPending("id3", "2026-09-12T08:20:00Z")

        storagePort.throwOnKey = "key-id1"
        storagePort.returnFalseOnKey = "key-id2"

        service.cleanup(pendingTtl, batchSize = 2)

        assertEquals(FileStatus.PENDING, db["id1"]?.status)
        assertEquals(FileStatus.PENDING, db["id2"]?.status)
        assertEquals(FileStatus.FAILED, db["id3"]?.status)

        // Batch limit progression:
        // 1st: batch=2, failedSize=0 => limit=2. Returns [id1, id2]. Both fail. failedSize becomes 2.
        // 2nd: batch=2, failedSize=2 => limit=4. Returns [id1, id2, id3]. Skips id1, id2. id3 succeeds. size=3 < 4. Loop breaks.
        assertEquals(listOf(2, 4), calledLimits)
    }

    @Test
    fun `같은 실패 행이 계속 반환되어도 무한 루프에 빠지지 않는다`() = runBlocking {
        addPending("id1", "2026-09-12T08:00:00Z")

        storagePort.throwOnKey = "key-id1"

        service.cleanup(pendingTtl, batchSize = 2)

        // limit will be 2 on first iteration (size 1 < 2, so it breaks immediately after).
        // Let's add multiple to see loop break explicitly.
        addPending("id2", "2026-09-12T08:10:00Z")
        addPending("id3", "2026-09-12T08:20:00Z")
        storagePort.throwOnKey = "key-id2"
        storagePort.returnFalseOnKey = "key-id3"

        service.cleanup(pendingTtl, batchSize = 2)
        // Should terminate normally, no infinite loop.
        assertTrue(true)
    }

    private fun addPending(id: String, updatedAtStr: String) {
        val instant = Instant.parse(updatedAtStr)
        db[id] = FileMeta(id, "owner", "key-$id", 100L, "text/plain", "hash", FileStatus.PENDING, instant, instant)
    }

    class FakeFileStoragePort(
        private val deletedKeys: MutableList<String>
    ) : FileStoragePort {
        var throwOnKey: String? = null
        var returnFalseOnKey: String? = null

        override suspend fun store(key: String, reader: ChunkReader, knownSize: Long?, contentType: String, expectedChecksum: String?): StoredFile = throw NotImplementedError()
        override suspend fun load(key: String): ChunkReader? = null
        override suspend fun exists(key: String): Boolean = false
        override suspend fun delete(key: String): Boolean {
            if (key == throwOnKey) throw RuntimeException("Simulated exception for $key")
            if (key == returnFalseOnKey) return false
            deletedKeys.add(key)
            return true
        }
    }

    class FakeFileMetaRepositoryPort(
        private val db: MutableMap<String, FileMeta>,
        private val calledLimits: MutableList<Int>
    ) : FileMetaRepositoryPort {
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
            calledLimits.add(limit)
            return db.values.filter { it.status == FileStatus.PENDING && it.updatedAt.isBefore(cutoffTime) }
                .sortedBy { it.updatedAt }
                .take(limit)
        }
    }
}
