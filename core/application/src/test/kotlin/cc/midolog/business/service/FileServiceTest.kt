package cc.midolog.business.service

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.PresignedRequest
import cc.midolog.file.model.StoredFile
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.FilePresignPort
import cc.midolog.file.port.storage.FileStoragePort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional

class FileServiceTest {

    private val clock = Clock.fixed(Instant.parse("2026-09-12T10:00:00Z"), ZoneOffset.UTC)

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

        val fileService = FileService(clock, storagePort, repoPort)

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

        val fileService = FileService(clock, storagePort, repoPort)

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

        val fileService = FileService(clock, storagePort, repoPort)

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

        val fileService = FileService(clock, storagePort, repoPort)

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

    @Test
    fun `presignUpload 성공 시 PENDING 메타데이터 저장 및 PresignedRequest 반환`() = runBlocking {
        var savedMeta: FileMeta? = null
        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = null
            override suspend fun save(file: FileMeta): FileMeta {
                savedMeta = file
                return file
            }
            override suspend fun updateStatus(id: String, status: FileStatus): Boolean = true
            override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> = emptyList()
        }

        val presignPort = object : FilePresignPort {
            override val isSupported: Boolean = true
            override suspend fun presignUpload(key: String, expirationSeconds: Long, expectedSize: Long?, contentType: String): PresignedRequest {
                return presignUpload(key, expirationSeconds, expectedSize, contentType, null)
            }
            override suspend fun presignUpload(key: String, expirationSeconds: Long, expectedSize: Long?, contentType: String, expectedChecksum: String?): PresignedRequest {
                return PresignedRequest("https://s3.example.com/$key", "PUT", expirationSeconds, mapOf("Content-Type" to contentType))
            }
            override suspend fun presignDownload(key: String, expirationSeconds: Long): PresignedRequest {
                return PresignedRequest("https://s3.example.com/$key", "GET", expirationSeconds, emptyMap())
            }
        }

        val storagePort = object : FileStoragePort {
            override suspend fun store(k: String, r: ChunkReader, s: Long?, c: String, e: String?) = throw NotImplementedError()
            override suspend fun load(key: String): ChunkReader? = null
            override suspend fun delete(key: String): Boolean = true
            override suspend fun exists(key: String): Boolean = false
        }

        val fileService = FileService(clock, storagePort, repoPort, Optional.of(presignPort))
        val (meta, presigned) = fileService.presignUpload("owner1", "image/png", 5000L, "hash123")

        assertNotNull(savedMeta)
        assertEquals(FileStatus.PENDING, savedMeta!!.status)
        assertEquals("owner1", savedMeta!!.ownerId)
        assertEquals(5000L, savedMeta!!.sizeBytes)
        assertEquals("image/png", savedMeta!!.contentType)
        assertEquals("hash123", savedMeta!!.checksum)
        assertEquals(meta.id, savedMeta!!.id)

        assertEquals("PUT", presigned.method)
        assertEquals(300L, presigned.expirationSeconds)
        assertTrue(presigned.url.contains(savedMeta!!.storageKey))
    }

    @Test
    fun `presignPort 미제공 또는 미지원 시 presignUpload는 예외를 던진다`() = runBlocking {
        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = null
            override suspend fun save(file: FileMeta): FileMeta = file
            override suspend fun updateStatus(id: String, status: FileStatus): Boolean = true
            override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> = emptyList()
        }
        val storagePort = object : FileStoragePort {
            override suspend fun store(k: String, r: ChunkReader, s: Long?, c: String, e: String?) = throw NotImplementedError()
            override suspend fun load(key: String): ChunkReader? = null
            override suspend fun delete(key: String): Boolean = true
            override suspend fun exists(key: String): Boolean = false
        }

        val fileService = FileService(clock, storagePort, repoPort)
        assertThrows(UnsupportedOperationException::class.java) {
            runBlocking {
                fileService.presignUpload("owner1", "image/png", 100L, null)
            }
        }
    }

    @Test
    fun `finalizeUpload 성공 시 실제 HEAD 메타데이터로 검증 후 READY로 전이된다`() = runBlocking {
        val db = mutableMapOf<String, FileMeta>()
        val pendingMeta = FileMeta(
            id = "file-01",
            ownerId = "owner1",
            storageKey = "uuid-key-01",
            sizeBytes = 1000L,
            contentType = "image/png",
            checksum = "sha256-hash",
            status = FileStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        db["file-01"] = pendingMeta

        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = db[id]
            override suspend fun save(file: FileMeta): FileMeta {
                db[file.id] = file
                return file
            }
            override suspend fun updateStatus(id: String, status: FileStatus): Boolean {
                db[id]?.let { db[id] = it.copy(status = status) }
                return true
            }
            override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> = emptyList()
        }

        val storagePort = object : FileStoragePort {
            override suspend fun store(k: String, r: ChunkReader, s: Long?, c: String, e: String?) = throw NotImplementedError()
            override suspend fun load(key: String): ChunkReader? = null
            override suspend fun delete(key: String): Boolean = true
            override suspend fun exists(key: String): Boolean = true
            override suspend fun head(key: String): cc.midolog.file.model.FileMetadata? {
                return cc.midolog.file.model.FileMetadata(
                    sizeBytes = 1000L,
                    contentType = "image/png",
                    checksum = "sha256-hash",
                    eTag = "etag-1"
                )
            }
        }

        val fileService = FileService(clock, storagePort, repoPort)
        val result = fileService.finalizeUpload("file-01", "owner1", 10000L)

        assertEquals(FileStatus.READY, result.status)
        assertEquals(1000L, result.sizeBytes)
        assertEquals("image/png", result.contentType)
        assertEquals("sha256-hash", result.checksum)
        assertEquals(FileStatus.READY, db["file-01"]?.status)
    }

    @Test
    fun `finalizeUpload는 이미 READY인 파일에 대해 멱등하게 성공한다`() = runBlocking {
        var headCalled = false
        val readyMeta = FileMeta(
            id = "file-ready",
            ownerId = "owner1",
            storageKey = "uuid-key-ready",
            sizeBytes = 500L,
            contentType = "text/plain",
            checksum = "abc",
            status = FileStatus.READY,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = if (id == "file-ready") readyMeta else null
            override suspend fun save(file: FileMeta): FileMeta = file
            override suspend fun updateStatus(id: String, status: FileStatus): Boolean = true
            override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> = emptyList()
        }
        val storagePort = object : FileStoragePort {
            override suspend fun store(k: String, r: ChunkReader, s: Long?, c: String, e: String?) = throw NotImplementedError()
            override suspend fun load(key: String): ChunkReader? = null
            override suspend fun delete(key: String): Boolean = true
            override suspend fun exists(key: String): Boolean = true
            override suspend fun head(key: String): cc.midolog.file.model.FileMetadata? {
                headCalled = true
                return null
            }
        }

        val fileService = FileService(clock, storagePort, repoPort)
        val result = fileService.finalizeUpload("file-ready", "owner1", 10000L)

        assertEquals(FileStatus.READY, result.status)
        assertFalse(headCalled, "이미 READY 상태이면 추가 HEAD 조회가 발생하지 않아야 한다")
    }

    @Test
    fun `finalizeUpload 시 스토리지에 파일이 없거나 불일치하면 FAILED 전이 및 스토리지 객체 정리 후 예외를 던진다`() = runBlocking {
        var deleteCalled = false
        val pendingMeta = FileMeta(
            id = "file-mismatch",
            ownerId = "owner1",
            storageKey = "key-mismatch",
            sizeBytes = 1000L,
            contentType = "image/png",
            checksum = "expected-hash",
            status = FileStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val db = mutableMapOf("file-mismatch" to pendingMeta)

        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = db[id]
            override suspend fun save(file: FileMeta): FileMeta {
                db[file.id] = file
                return file
            }
            override suspend fun updateStatus(id: String, status: FileStatus): Boolean {
                db[id]?.let { db[id] = it.copy(status = status) }
                return true
            }
            override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> = emptyList()
        }

        val storagePort = object : FileStoragePort {
            override suspend fun store(k: String, r: ChunkReader, s: Long?, c: String, e: String?) = throw NotImplementedError()
            override suspend fun load(key: String): ChunkReader? = null
            override suspend fun delete(key: String): Boolean {
                deleteCalled = true
                return true
            }
            override suspend fun exists(key: String): Boolean = true
            override suspend fun head(key: String): cc.midolog.file.model.FileMetadata? {
                // 크기 불일치 시뮬레이션 (1000L != 2000L)
                return cc.midolog.file.model.FileMetadata(sizeBytes = 2000L, contentType = "image/png", checksum = "expected-hash")
            }
        }

        val fileService = FileService(clock, storagePort, repoPort)

        assertThrows(cc.midolog.web.exception.ApiException::class.java) {
            runBlocking {
                fileService.finalizeUpload("file-mismatch", "owner1", 10000L)
            }
        }

        assertEquals(FileStatus.FAILED, db["file-mismatch"]?.status, "크기 불일치 시 FAILED로 전이되어야 한다")
        assertTrue(deleteCalled, "불일치 발생 시 고아 객체 정리를 위해 delete가 호출되어야 한다")
    }

    @Test
    fun `finalizeUpload 시 다른 소유자가 요청하면 404를 반환하고 스토리지에 접근하지 않는다`() = runBlocking {
        var headCalled = false
        val pendingMeta = FileMeta(
            id = "file-hacker",
            ownerId = "owner1",
            storageKey = "key-hacker",
            sizeBytes = 1000L,
            contentType = "image/png",
            checksum = "expected-hash",
            status = FileStatus.PENDING,
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )
        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = if (id == "file-hacker") pendingMeta else null
            override suspend fun save(file: FileMeta): FileMeta = file
            override suspend fun updateStatus(id: String, status: FileStatus): Boolean = true
            override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> = emptyList()
        }
        val storagePort = object : FileStoragePort {
            override suspend fun store(k: String, r: ChunkReader, s: Long?, c: String, e: String?) = throw NotImplementedError()
            override suspend fun load(key: String): ChunkReader? = null
            override suspend fun delete(key: String): Boolean = true
            override suspend fun exists(key: String): Boolean = true
            override suspend fun head(key: String): cc.midolog.file.model.FileMetadata? {
                headCalled = true
                return null
            }
        }

        val fileService = FileService(clock, storagePort, repoPort)

        assertThrows(cc.midolog.web.exception.ApiException::class.java) {
            runBlocking {
                fileService.finalizeUpload("file-hacker", "attacker", 10000L)
            }
        }

        assertFalse(headCalled, "타 소유자 요청 시 스토리지 조회가 발생하지 않아야 한다")
    }
}
