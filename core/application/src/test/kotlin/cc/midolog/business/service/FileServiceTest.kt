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
    fun `성공 시 PENDING 저장 후 READY로 메타가 갱신되며, 생성된 ID와 속성이 올바르다`() = runBlocking {
        val savedMetas = mutableListOf<FileMeta>()

        val storagePort = object : FileStoragePort {
            override suspend fun store(key: String, reader: ChunkReader, knownSize: Long?, contentType: String, expectedChecksum: String?): StoredFile {
                return StoredFile(id = "1", ownerId = "owner1", storageKey = key, sizeBytes = 1234L, contentType = "text/plain", checksum = "abcde", status = FileStatus.READY)
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

        val fileService = FileService(storagePort, repoPort)

        val reader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int = -1
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        val result = fileService.uploadFile("owner1", "text/html", reader)

        assertEquals(2, savedMetas.size, "PENDING and then READY saves")
        val pendingMeta = savedMetas[0]
        val readyMeta = savedMetas[1]

        assertEquals(FileStatus.PENDING, pendingMeta.status)
        assertEquals(FileStatus.READY, readyMeta.status)

        assertEquals(pendingMeta.id, readyMeta.id)
        assertEquals(26, readyMeta.id.length, "ULID should be 26 characters")
        assertEquals("owner1", readyMeta.ownerId)

        val uuidRegex = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
        assertTrue(readyMeta.storageKey.matches(uuidRegex), "storageKey should be a UUID")

        assertEquals(1234L, readyMeta.sizeBytes)
        assertEquals("text/plain", readyMeta.contentType)
        assertEquals("abcde", readyMeta.checksum)

        assertEquals(result, readyMeta)
    }

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
        var failedId: String? = null
        val savedMetas = mutableListOf<FileMeta>()

        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = null
            override suspend fun save(file: FileMeta): FileMeta {
                savedMetas.add(file)
                return file
            }
            override suspend fun updateStatus(id: String, status: FileStatus): Boolean {
                failedId = id
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

        assertEquals(1, savedMetas.size, "Should have saved PENDING meta")
        val pendingMeta = savedMetas[0]

        assertEquals(FileStatus.FAILED, savedStatus)
        assertEquals(pendingMeta.id, failedId, "Failed ID should match the pending ID")
    }

    @Test
    fun `다른 owner로 getFile, deleteFile, loadContent 요청 시 NOT_FOUND로 실패하며 storage 접근이 발생하지 않는다`() = runBlocking {
        var storageLoadCount = 0
        var storageDeleteCount = 0

        val storagePort = object : FileStoragePort {
            override suspend fun store(key: String, reader: ChunkReader, knownSize: Long?, contentType: String, expectedChecksum: String?): StoredFile {
                return StoredFile(id = "1", ownerId = "owner1", storageKey = key, sizeBytes = 100L, contentType = "text/plain", checksum = "abc", status = FileStatus.READY)
            }
            override suspend fun load(key: String): ChunkReader? {
                storageLoadCount++
                return null
            }
            override suspend fun delete(key: String): Boolean {
                storageDeleteCount++
                return true
            }
            override suspend fun exists(key: String): Boolean = false
        }

        val existingMeta = FileMeta(
            id = "file1",
            ownerId = "owner1",
            storageKey = "storage1",
            sizeBytes = 100L,
            contentType = "text/plain",
            checksum = "abc",
            status = FileStatus.READY,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = if (id == "file1") existingMeta else null
            override suspend fun save(file: FileMeta): FileMeta = file
            override suspend fun updateStatus(id: String, status: FileStatus): Boolean = true
            override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> = emptyList()
        }

        val fileService = FileService(storagePort, repoPort)

        // 1. getFile
        val getEx = assertThrows(cc.midolog.web.exception.ApiException::class.java) {
            runBlocking {
                fileService.getFile("file1", "hacker")
            }
        }
        assertEquals("file not found", getEx.message)

        // 2. deleteFile
        val deleteEx = assertThrows(cc.midolog.web.exception.ApiException::class.java) {
            runBlocking {
                fileService.deleteFile("file1", "hacker")
            }
        }
        assertEquals("file not found", deleteEx.message)

        // 3. loadContent
        val loadEx = assertThrows(cc.midolog.web.exception.ApiException::class.java) {
            runBlocking {
                fileService.loadContent("file1", "hacker")
            }
        }
        assertEquals("file not found", loadEx.message)

        assertEquals(0, storageLoadCount, "Storage load should not be called")
        assertEquals(0, storageDeleteCount, "Storage delete should not be called")
    }

    @Test
    fun `deleteFile 시 storage delete가 false를 반환하거나 예외를 던지면 상태가 변경되지 않는다`() = runBlocking {
        var storageDeleteCalledCount = 0
        val storagePort = object : FileStoragePort {
            override suspend fun store(key: String, reader: ChunkReader, knownSize: Long?, contentType: String, expectedChecksum: String?): StoredFile = throw NotImplementedError()
            override suspend fun load(key: String): ChunkReader? = null
            override suspend fun delete(key: String): Boolean {
                storageDeleteCalledCount++
                if (key == "throw") throw RuntimeException("Storage delete error")
                return key != "false_return"
            }
            override suspend fun exists(key: String): Boolean = false
        }

        val db = mutableMapOf(
            "file1" to FileMeta("file1", "owner1", "throw", 100L, "text/plain", "abc", FileStatus.READY, Instant.now(), Instant.now()),
            "file2" to FileMeta("file2", "owner1", "false_return", 100L, "text/plain", "abc", FileStatus.READY, Instant.now(), Instant.now())
        )

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
            override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> = emptyList()
        }

        val fileService = FileService(storagePort, repoPort)

        // 1. Exception case
        assertThrows(RuntimeException::class.java) {
            runBlocking {
                fileService.deleteFile("file1", "owner1")
            }
        }
        assertEquals(FileStatus.READY, db["file1"]?.status, "Status should not change on exception")
        
        // 2. False return case
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                fileService.deleteFile("file2", "owner1")
            }
        }
        assertEquals(FileStatus.READY, db["file2"]?.status, "Status should not change when delete returns false")
        
        assertEquals(2, storageDeleteCalledCount)
    }
}
