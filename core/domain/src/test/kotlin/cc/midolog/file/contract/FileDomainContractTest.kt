package cc.midolog.file.contract

import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.PresignedRequest
import cc.midolog.file.model.StoredFile
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.ChunkWriter
import cc.midolog.file.port.storage.FilePresignPort
import cc.midolog.file.port.storage.FileStoragePort
import java.io.Closeable
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FileDomainContractTest {

    private fun runSuspend(block: suspend () -> Unit) {
        var failure: Throwable? = null
        val continuation = object : Continuation<Unit> {
            override val context: CoroutineContext = EmptyCoroutineContext
            override fun resumeWith(result: Result<Unit>) {
                failure = result.exceptionOrNull()
            }
        }
        block.startCoroutine(continuation)
        failure?.let { throw it }
    }

    @Test
    fun `FileStatus enum contracts are preserved`() {
        val expectedStatuses = setOf("PENDING", "READY", "FAILED", "DELETED")
        val actualStatuses = FileStatus.entries.map { it.name }.toSet()

        assertEquals(expectedStatuses, actualStatuses, "FileStatus must contain exactly PENDING, READY, FAILED, DELETED")
        assertEquals(4, FileStatus.entries.size)

        // Exhaustive pattern matching to guard all states at compile time
        val mapped = FileStatus.entries.map { status ->
            when (status) {
                FileStatus.PENDING -> "pending"
                FileStatus.READY -> "ready"
                FileStatus.FAILED -> "failed"
                FileStatus.DELETED -> "deleted"
            }
        }
        assertEquals(listOf("pending", "ready", "failed", "deleted"), mapped)
    }

    @Test
    fun `StoredFile data model contract and properties`() {
        val file = StoredFile(
            id = "01ARZ3NDEKTSV4RRFFQ69G5FAV",
            ownerId = "user_123",
            storageKey = "c9bf9e57-1685-4c89-bafb-ff5af830be8a",
            sizeBytes = 1048576L,
            contentType = "image/png",
            checksum = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            status = FileStatus.READY,
        )

        assertEquals("01ARZ3NDEKTSV4RRFFQ69G5FAV", file.id)
        assertEquals("user_123", file.ownerId)
        assertEquals("c9bf9e57-1685-4c89-bafb-ff5af830be8a", file.storageKey)
        assertEquals(1048576L, file.sizeBytes)
        assertEquals("image/png", file.contentType)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", file.checksum)
        assertEquals(FileStatus.READY, file.status)

        val pendingFile = file.copy(checksum = null, status = FileStatus.PENDING)
        assertNull(pendingFile.checksum)
        assertEquals(FileStatus.PENDING, pendingFile.status)
    }

    @Test
    fun `PresignedRequest data model contract`() {
        val presigned = PresignedRequest(
            url = "https://storage.midolog.cc/bucket/sample.png?signature=xyz",
            method = "PUT",
            expirationSeconds = 3600L,
            requiredHeaders = mapOf("Content-Type" to "image/png", "x-amz-acl" to "private"),
        )

        assertEquals("https://storage.midolog.cc/bucket/sample.png?signature=xyz", presigned.url)
        assertEquals("PUT", presigned.method)
        assertEquals(3600L, presigned.expirationSeconds)
        assertEquals(2, presigned.requiredHeaders.size)
        assertEquals("image/png", presigned.requiredHeaders["Content-Type"])
    }

    @Test
    fun `ChunkReader and ChunkWriter contract verification`() = runSuspend {
        var closedReader = false
        var canceledReaderCause: Throwable? = null
        val testReader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int {
                buffer[0] = 42
                return 1
            }

            override suspend fun cancel(cause: Throwable?) {
                canceledReaderCause = cause
            }

            override fun close() {
                closedReader = true
            }
        }

        assertTrue(Closeable::class.java.isAssignableFrom(ChunkReader::class.java))
        val buf = ByteArray(10)
        val readBytes = testReader.readChunk(buf)
        assertEquals(1, readBytes)
        assertEquals(42.toByte(), buf[0])

        val testException = IllegalStateException("cancellation reason")
        testReader.cancel(testException)
        assertEquals(testException, canceledReaderCause)
        testReader.close()
        assertTrue(closedReader)

        var closedWriter = false
        var writtenLength = 0
        var canceledWriterCause: Throwable? = null
        val testWriter = object : ChunkWriter {
            override suspend fun writeChunk(buffer: ByteArray, length: Int) {
                writtenLength = length
            }

            override suspend fun cancel(cause: Throwable?) {
                canceledWriterCause = cause
            }

            override fun close() {
                closedWriter = true
            }
        }

        assertTrue(Closeable::class.java.isAssignableFrom(ChunkWriter::class.java))
        testWriter.writeChunk(buf, 5)
        assertEquals(5, writtenLength)
        testWriter.cancel(testException)
        assertEquals(testException, canceledWriterCause)
        testWriter.close()
        assertTrue(closedWriter)
    }

    @Test
    fun `FileStoragePort and FilePresignPort interface contracts`() = runSuspend {
        val mockStorage = object : FileStoragePort {
            override suspend fun store(
                key: String,
                reader: ChunkReader,
                knownSize: Long?,
                contentType: String,
                expectedChecksum: String?,
            ): StoredFile {
                return StoredFile(
                    id = "id-1",
                    ownerId = "owner-1",
                    storageKey = key,
                    sizeBytes = knownSize ?: 0L,
                    contentType = contentType,
                    checksum = expectedChecksum,
                    status = FileStatus.READY,
                )
            }

            override suspend fun load(key: String): ChunkReader? {
                return if (key == "existing") {
                    object : ChunkReader {
                        override suspend fun readChunk(buffer: ByteArray): Int = -1
                        override suspend fun cancel(cause: Throwable?) {}
                        override fun close() {}
                    }
                } else {
                    null
                }
            }

            override suspend fun delete(key: String): Boolean = true

            override suspend fun exists(key: String): Boolean = key == "existing"
        }

        val emptyReader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int = -1
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        val stored = mockStorage.store("key1", emptyReader, 100L, "text/plain", "hash")
        assertEquals("key1", stored.storageKey)
        assertEquals(100L, stored.sizeBytes)
        assertEquals("text/plain", stored.contentType)
        assertEquals("hash", stored.checksum)
        assertEquals(FileStatus.READY, stored.status)

        assertNotNull(mockStorage.load("existing"))
        assertNull(mockStorage.load("non-existing"))
        assertTrue(mockStorage.delete("key1"))
        assertTrue(mockStorage.exists("existing"))
        assertFalse(mockStorage.exists("non-existing"))

        val mockPresign = object : FilePresignPort {
            override val isSupported: Boolean = true

            override suspend fun presignUpload(
                key: String,
                expirationSeconds: Long,
                expectedSize: Long?,
                contentType: String,
            ): PresignedRequest {
                return PresignedRequest(
                    url = "https://upload.example.com/$key",
                    method = "PUT",
                    expirationSeconds = expirationSeconds,
                    requiredHeaders = mapOf("Content-Type" to contentType),
                )
            }

            override suspend fun presignDownload(
                key: String,
                expirationSeconds: Long,
            ): PresignedRequest {
                return PresignedRequest(
                    url = "https://download.example.com/$key",
                    method = "GET",
                    expirationSeconds = expirationSeconds,
                    requiredHeaders = emptyMap(),
                )
            }
        }

        assertTrue(mockPresign.isSupported)
        val uploadReq = mockPresign.presignUpload("k1", 60L, 10L, "application/json")
        assertEquals("https://upload.example.com/k1", uploadReq.url)
        assertEquals("PUT", uploadReq.method)
        assertEquals(60L, uploadReq.expirationSeconds)
        assertEquals("application/json", uploadReq.requiredHeaders["Content-Type"])

        val downloadReq = mockPresign.presignDownload("k1", 120L)
        assertEquals("https://download.example.com/k1", downloadReq.url)
        assertEquals("GET", downloadReq.method)
        assertEquals(120L, downloadReq.expirationSeconds)
    }

    @Test
    fun `FileMetaRepositoryPort draft interface contract`() = runSuspend {
        val storage = mutableMapOf<String, StoredFile>()

        val mockRepo = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): StoredFile? = storage[id]

            override suspend fun save(file: StoredFile): StoredFile {
                storage[file.id] = file
                return file
            }

            override suspend fun updateStatus(id: String, status: FileStatus): Boolean {
                val current = storage[id] ?: return false
                storage[id] = current.copy(status = status)
                return true
            }

            override suspend fun findExpiredPending(limit: Int): List<StoredFile> {
                return storage.values
                    .filter { it.status == FileStatus.PENDING }
                    .take(limit)
            }
        }

        val sampleFile = StoredFile(
            id = "file-01",
            ownerId = "owner-01",
            storageKey = "uuid-key-01",
            sizeBytes = 2048L,
            contentType = "application/pdf",
            checksum = null,
            status = FileStatus.PENDING,
        )

        // save
        val saved = mockRepo.save(sampleFile)
        assertEquals("file-01", saved.id)
        assertEquals(FileStatus.PENDING, saved.status)

        // findById
        val found = mockRepo.findById("file-01")
        assertNotNull(found)
        assertEquals("file-01", found.id)
        assertNull(mockRepo.findById("file-99"))

        // findExpiredPending
        val expired = mockRepo.findExpiredPending(10)
        assertEquals(1, expired.size)
        assertEquals("file-01", expired[0].id)

        // updateStatus
        val updated = mockRepo.updateStatus("file-01", FileStatus.READY)
        assertTrue(updated)
        assertEquals(FileStatus.READY, mockRepo.findById("file-01")?.status)
        assertFalse(mockRepo.updateStatus("file-99", FileStatus.READY))

        // After update to READY, expired pending should be empty
        assertEquals(0, mockRepo.findExpiredPending(10).size)
    }
}
