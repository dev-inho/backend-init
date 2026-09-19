package cc.midolog.storage.file.s3

import cc.midolog.file.port.storage.ChunkReader
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.CompletableFuture

class S3FileStorageAdapterTest {

    private val s3AsyncClient = mock(S3AsyncClient::class.java)
    private val bucket = "test-bucket"
    private val adapter = S3FileStorageAdapter(s3AsyncClient, bucket)

    @Test
    fun `유효하지 않은 UUID 키는 거부해야 한다`() = runBlocking {
        val invalidKey = "not-a-uuid"
        val reader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int = -1
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        assertThrows<IllegalArgumentException> {
            runBlocking {
                adapter.store(invalidKey, reader, null, "text/plain", null)
            }
        }
    }

    @Test
    fun `exists는 객체가 있으면 true, 없으면 false를 반환해야 한다`() = runBlocking {
        val key = UUID.randomUUID().toString()

        val headFuture = CompletableFuture.completedFuture(HeadObjectResponse.builder().contentLength(100).build())
        `when`(s3AsyncClient.headObject(any<HeadObjectRequest>())).thenReturn(headFuture)
        assertTrue(adapter.exists(key))

        val notFoundFuture = CompletableFuture<HeadObjectResponse>()
        notFoundFuture.completeExceptionally(NoSuchKeyException.builder().message("NoSuchKey").build())
        `when`(s3AsyncClient.headObject(any<HeadObjectRequest>())).thenReturn(notFoundFuture)
        assertFalse(adapter.exists(key))
    }

    @Test
    fun `head는 실제 메타데이터를 반환하고 미존재 시 null을 반환해야 한다`() = runBlocking {
        val key = UUID.randomUUID().toString()

        val headResponse = HeadObjectResponse.builder()
            .contentLength(1024L)
            .contentType("image/png")
            .eTag("\"etag123\"")
            .metadata(mapOf("sha256" to "mocked-sha256"))
            .build()
        `when`(s3AsyncClient.headObject(any<HeadObjectRequest>()))
            .thenReturn(CompletableFuture.completedFuture(headResponse))

        val metadata = adapter.head(key)
        assertNotNull(metadata)
        assertEquals(1024L, metadata!!.sizeBytes)
        assertEquals("image/png", metadata.contentType)
        assertEquals("etag123", metadata.eTag)
        assertEquals("mocked-sha256", metadata.checksum)

        val notFoundFuture = CompletableFuture<HeadObjectResponse>()
        notFoundFuture.completeExceptionally(NoSuchKeyException.builder().message("NoSuchKey").build())
        `when`(s3AsyncClient.headObject(any<HeadObjectRequest>())).thenReturn(notFoundFuture)
        assertNull(adapter.head(key))
    }

    @Test
    fun `delete는 멱등성을 보장해야 한다`() = runBlocking {
        val key = UUID.randomUUID().toString()

        `when`(s3AsyncClient.deleteObject(any<DeleteObjectRequest>()))
            .thenReturn(CompletableFuture.completedFuture(DeleteObjectResponse.builder().build()))
        assertTrue(adapter.delete(key))

        val notFoundFuture = CompletableFuture<DeleteObjectResponse>()
        notFoundFuture.completeExceptionally(NoSuchKeyException.builder().message("NoSuchKey").build())
        `when`(s3AsyncClient.deleteObject(any<DeleteObjectRequest>())).thenReturn(notFoundFuture)
        assertTrue(adapter.delete(key))
    }

    @Test
    fun `store는 데이터를 읽어 S3에 업로드하고 StoredFile을 반환해야 한다`() = runBlocking {
        val key = UUID.randomUUID().toString()
        val data = "Hello, S3 Storage!".toByteArray()
        var readOffset = 0

        val reader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int {
                if (readOffset >= data.size) return -1
                val len = minOf(buffer.size, data.size - readOffset)
                System.arraycopy(data, readOffset, buffer, 0, len)
                readOffset += len
                return len
            }
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        `when`(s3AsyncClient.putObject(any<PutObjectRequest>(), any<AsyncRequestBody>()))
            .thenReturn(CompletableFuture.completedFuture(PutObjectResponse.builder().build()))

        val stored = adapter.store(key, reader, data.size.toLong(), "text/plain", null)
        assertEquals(key, stored.storageKey)
        assertEquals(data.size.toLong(), stored.sizeBytes)
        assertEquals("text/plain", stored.contentType)
        assertNotNull(stored.checksum)
    }
}
