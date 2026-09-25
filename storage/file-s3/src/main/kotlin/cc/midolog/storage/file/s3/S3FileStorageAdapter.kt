package cc.midolog.storage.file.s3

import cc.midolog.file.model.FileMetadata
import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.StoredFile
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.FileStoragePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import software.amazon.awssdk.core.async.AsyncRequestBody
import software.amazon.awssdk.core.async.AsyncResponseTransformer
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.ChecksumAlgorithm
import software.amazon.awssdk.services.s3.model.ChecksumMode
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import java.io.File
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * AWS SDK S3 비동기 클라이언트를 활용한 파일 저장소 어댑터.
 *
 * S3와의 모든 I/O를 논블로킹 비동기로 수행하며, 대용량 파일 스트리밍 시 임시 디스크 스풀링을 적용하여 힙 메모리 OOM을 방지한다.
 * 네이티브 SHA-256 체크섬 검증과 안전한 예외 래핑을 지원한다.
 */
class S3FileStorageAdapter(
    private val s3AsyncClient: S3AsyncClient,
    private val bucket: String,
) : FileStoragePort {

    private fun validateKey(key: String) {
        try {
            UUID.fromString(key)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid UUID format for storage key: $key")
        }
        if (key.contains("/") || key.contains("..") || key.contains("\\")) {
            throw SecurityException("Path traversal attempt in storage key: $key")
        }
    }

    private fun wrapStorageException(cause: Throwable, operation: String, key: String): Throwable {
        val actual = if (cause is CompletionException) cause.cause ?: cause else cause
        if (actual is IllegalArgumentException || actual is SecurityException || actual is IllegalStateException) {
            return actual
        }
        val sanitizedMessage = actual.message?.replace(bucket, "***") ?: "Storage operation failed"
        val statusCode = (actual as? S3Exception)?.statusCode()
        val codeStr = if (statusCode != null) "[status=$statusCode] " else ""
        return IllegalStateException("S3 $operation failed for key $key: $codeStr$sanitizedMessage", actual)
    }

    private fun toBase64Checksum(checksum: String): String {
        return try {
            if (checksum.length == 64 && checksum.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
                val bytes = ByteArray(32)
                for (i in 0 until 32) {
                    bytes[i] = checksum.substring(i * 2, i * 2 + 2).toInt(16).toByte()
                }
                Base64.getEncoder().encodeToString(bytes)
            } else {
                checksum
            }
        } catch (_: Exception) {
            checksum
        }
    }

    private fun normalizeChecksumToHex(checksum: String): String {
        return try {
            if (checksum.length == 44 && (checksum.endsWith("=") || checksum.contains("/"))) {
                val bytes = Base64.getDecoder().decode(checksum)
                bytes.joinToString("") { "%02x".format(it) }
            } else {
                checksum
            }
        } catch (_: Exception) {
            checksum
        }
    }

    override suspend fun store(
        key: String,
        reader: ChunkReader,
        knownSize: Long?,
        contentType: String,
        expectedChecksum: String?,
    ): StoredFile {
        validateKey(key)

        val tempFile = withContext(Dispatchers.IO) {
            File.createTempFile("s3-upload-$key-", ".tmp")
        }
        val buffer = ByteArray(8192)
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L

        try {
            withContext(Dispatchers.IO) {
                tempFile.outputStream().use { output ->
                    while (true) {
                        val read = reader.readChunk(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                        size += read
                    }
                    output.flush()
                }
            }

            val checksum = digest.digest().joinToString("") { "%02x".format(it) }

            if (expectedChecksum != null) {
                val normalizedExpected = normalizeChecksumToHex(expectedChecksum)
                if (!normalizedExpected.equals(checksum, ignoreCase = true)) {
                    throw IllegalStateException("체크섬이 다릅니다")
                }
            }
            if (knownSize != null && knownSize != size) {
                throw IllegalStateException("크기가 다릅니다")
            }

            val base64Checksum = toBase64Checksum(checksum)
            val metadataMap = mutableMapOf("sha256" to checksum)
            val putRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(size)
                .checksumAlgorithm(ChecksumAlgorithm.SHA256)
                .checksumSHA256(base64Checksum)
                .metadata(metadataMap)
                .build()

            try {
                s3AsyncClient.putObject(putRequest, AsyncRequestBody.fromFile(tempFile)).await()
            } catch (e: Exception) {
                throw wrapStorageException(e, "store", key)
            }

            return StoredFile(
                id = "",
                ownerId = "",
                storageKey = key,
                sizeBytes = size,
                contentType = contentType,
                checksum = checksum,
                status = FileStatus.READY,
            )
        } catch (e: Exception) {
            reader.cancel(e)
            throw wrapStorageException(e, "store", key)
        } finally {
            withContext(Dispatchers.IO) {
                try {
                    if (tempFile.exists()) {
                        tempFile.delete()
                    }
                } catch (_: Exception) {
                }
            }
            reader.close()
        }
    }

    override suspend fun load(key: String): ChunkReader? {
        validateKey(key)

        val getRequest = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()

        return try {
            val responsePublisher = s3AsyncClient.getObject(getRequest, AsyncResponseTransformer.toPublisher()).await()
            S3PublisherChunkReader(responsePublisher)
        } catch (e: Exception) {
            val actual = if (e is CompletionException) e.cause ?: e else e
            if (actual is NoSuchKeyException || actual.message?.contains("NoSuchKey") == true || actual.message?.contains("404") == true) {
                null
            } else {
                throw wrapStorageException(actual, "load", key)
            }
        }
    }

    override suspend fun delete(key: String): Boolean {
        validateKey(key)
        val deleteRequest = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()

        return try {
            s3AsyncClient.deleteObject(deleteRequest).await()
            true
        } catch (e: Exception) {
            val actual = if (e is CompletionException) e.cause ?: e else e
            if (actual is NoSuchKeyException || actual.message?.contains("NoSuchKey") == true) {
                true
            } else {
                throw wrapStorageException(actual, "delete", key)
            }
        }
    }

    override suspend fun exists(key: String): Boolean {
        validateKey(key)
        val headRequest = HeadObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()

        return try {
            s3AsyncClient.headObject(headRequest).await()
            true
        } catch (e: Exception) {
            val actual = if (e is CompletionException) e.cause ?: e else e
            if (actual is NoSuchKeyException || actual.message?.contains("NoSuchKey") == true || actual.message?.contains("404") == true) {
                false
            } else {
                throw wrapStorageException(actual, "exists", key)
            }
        }
    }

    override suspend fun head(key: String): FileMetadata? {
        validateKey(key)
        val headRequest = HeadObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .checksumMode(ChecksumMode.ENABLED)
            .build()

        return try {
            val response = s3AsyncClient.headObject(headRequest).await()
            val rawChecksum = response.checksumSHA256() ?: response.metadata()["sha256"]
            val normalizedChecksum = rawChecksum?.let { normalizeChecksumToHex(it) }
            FileMetadata(
                sizeBytes = response.contentLength(),
                contentType = response.contentType(),
                checksum = normalizedChecksum,
                eTag = response.eTag()?.trim('"'),
            )
        } catch (e: Exception) {
            val actual = if (e is CompletionException) e.cause ?: e else e
            if (actual is NoSuchKeyException || actual.message?.contains("NoSuchKey") == true || actual.message?.contains("404") == true) {
                null
            } else {
                throw wrapStorageException(actual, "head", key)
            }
        }
    }
}

/**
 * CompletableFuture를 코루틴 환경에서 비동기 논블로킹으로 대기하는 유틸리티 확장 함수.
 */
internal suspend fun <T> CompletableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        whenComplete { result, exception ->
            if (exception != null) {
                val cause = if (exception is CompletionException) exception.cause ?: exception else exception
                cont.resumeWithException(cause)
            } else {
                cont.resume(result)
            }
        }
        cont.invokeOnCancellation {
            cancel(true)
        }
    }

/**
 * S3 비동기 응답 Publisher를 ChunkReader로 변환하는 어댑터 클래스.
 */
private class S3PublisherChunkReader(
    publisher: org.reactivestreams.Publisher<ByteBuffer>,
) : ChunkReader {

    private val queue = ConcurrentLinkedQueue<ByteBuffer>()
    @Volatile
    private var isCompleted = false
    @Volatile
    private var failure: Throwable? = null
    @Volatile
    private var subscription: org.reactivestreams.Subscription? = null

    init {
        publisher.subscribe(object : org.reactivestreams.Subscriber<ByteBuffer> {
            override fun onSubscribe(s: org.reactivestreams.Subscription) {
                subscription = s
                s.request(Long.MAX_VALUE)
            }

            override fun onNext(buffer: ByteBuffer) {
                val copy = ByteBuffer.allocate(buffer.remaining())
                copy.put(buffer)
                copy.flip()
                queue.add(copy)
            }

            override fun onError(t: Throwable) {
                failure = t
                isCompleted = true
            }

            override fun onComplete() {
                isCompleted = true
            }
        })
    }

    override suspend fun readChunk(buffer: ByteArray): Int = withContext(Dispatchers.IO) {
        failure?.let { throw it }

        while (queue.isEmpty()) {
            if (isCompleted) {
                failure?.let { throw it }
                return@withContext -1
            }
            kotlinx.coroutines.delay(10)
        }

        var totalRead = 0
        while (!queue.isEmpty() && totalRead < buffer.size) {
            val byteBuffer = queue.peek() ?: break
            val toRead = minOf(byteBuffer.remaining(), buffer.size - totalRead)
            byteBuffer.get(buffer, totalRead, toRead)
            totalRead += toRead

            if (!byteBuffer.hasRemaining()) {
                queue.poll()
            }
        }

        if (totalRead > 0) totalRead else -1
    }

    override suspend fun cancel(cause: Throwable?) {
        subscription?.cancel()
        queue.clear()
        isCompleted = true
    }

    override fun close() {
        subscription?.cancel()
        queue.clear()
        isCompleted = true
    }
}
