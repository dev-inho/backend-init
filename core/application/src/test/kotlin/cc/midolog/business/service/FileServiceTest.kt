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
import java.time.Instant

class FileServiceTest {

    @Test
    fun `스토리지 저장 실패 시 PENDING 메타가 FAILED로 전이된다`() = runBlocking {
        val storagePort = object : FileStoragePort {
            override suspend fun store(key: String, reader: ChunkReader, knownSize: Long?, contentType: String, expectedChecksum: String?): StoredFile {
                throw RuntimeException("Storage failed")
            }
            override suspend fun load(key: String): ChunkReader? = null
            override suspend fun delete(key: String): Boolean = true
            override suspend fun exists(key: String): Boolean = false
        }

        var savedStatus: FileStatus? = null

        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = null
            override suspend fun save(file: FileMeta): FileMeta = file
            override suspend fun updateStatus(id: String, status: FileStatus): Boolean {
                savedStatus = status
                return true
            }
            override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> = emptyList()
        }

        val fileService = FileService(storagePort, repoPort)
        
        val reader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int = -1
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        assertThrows(RuntimeException::class.java) {
            runBlocking {
                fileService.uploadFile("owner1", "text/plain", reader)
            }
        }

        assertEquals(FileStatus.FAILED, savedStatus)
    }
}
