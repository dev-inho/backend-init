package cc.midolog.business.service

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.StoredFile
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.FileStoragePort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class FileServiceClockTest {

    @Test
    fun `주입된 Clock 시각이 PENDING 저장과 READY 저장 및 반환값에 일관되게 반영된다`() = runBlocking {
        val fixedInstant = Instant.parse("2020-01-01T00:00:00Z")
        val clock = Clock.fixed(fixedInstant, ZoneOffset.UTC)

        val savedMetas = mutableListOf<FileMeta>()

        val storagePort = object : FileStoragePort {
            override suspend fun store(
                key: String,
                reader: ChunkReader,
                knownSize: Long?,
                contentType: String,
                expectedChecksum: String?
            ): StoredFile {
                return StoredFile(
                    id = "id-1",
                    ownerId = "owner-1",
                    storageKey = key,
                    sizeBytes = 100L,
                    contentType = contentType,
                    checksum = "checksum-1",
                    status = FileStatus.READY
                )
            }

            override suspend fun load(key: String): ChunkReader? = null
            override suspend fun delete(key: String): Boolean = true
            override suspend fun exists(key: String): Boolean = false
        }

        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = null
            override suspend fun save(file: FileMeta): FileMeta {
                savedMetas.add(file)
                return file
            }

            override suspend fun updateStatus(id: String, status: FileStatus): Boolean = true
            override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> = emptyList()
        }

        val fileService = FileService(clock, storagePort, repoPort)

        val reader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int = -1
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        val result = fileService.uploadFile("owner-1", "application/octet-stream", reader)

        // 저장 호출 2건 포착 검증
        assertEquals(2, savedMetas.size, "uploadFile should invoke save exactly twice (PENDING, READY)")

        val pendingSave = savedMetas[0]
        assertEquals(FileStatus.PENDING, pendingSave.status, "First save must be PENDING status")
        assertEquals(fixedInstant, pendingSave.createdAt, "PENDING createdAt must match injected clock")
        assertEquals(fixedInstant, pendingSave.updatedAt, "PENDING updatedAt must match injected clock")

        val readySave = savedMetas[1]
        assertEquals(FileStatus.READY, readySave.status, "Second save must transition to READY status")
        assertEquals(fixedInstant, readySave.createdAt, "READY createdAt must match injected clock")
        assertEquals(fixedInstant, readySave.updatedAt, "READY updatedAt must match injected clock")

        // 반환값 검증
        assertEquals(FileStatus.READY, result.status)
        assertEquals(fixedInstant, result.createdAt, "Return FileMeta createdAt must match injected clock")
        assertEquals(fixedInstant, result.updatedAt, "Return FileMeta updatedAt must match injected clock")
    }
}
