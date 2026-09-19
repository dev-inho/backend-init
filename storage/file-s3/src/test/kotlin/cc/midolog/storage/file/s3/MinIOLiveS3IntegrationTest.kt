package cc.midolog.storage.file.s3

import cc.midolog.file.port.storage.ChunkReader
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.TestMethodOrder
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.util.UUID

/**
 * MinIO 실물 컨테이너 환경을 대상으로 하는 S3 통합 테스트.
 *
 * Docker MinIO 환경(기본 포트 9000)에서 실행되며,
 * Presigned PUT 업로드, HEAD 기반 메타데이터 사후 검증(성공/불일치/미존재),
 * 및 객체 정리 흐름을 실제 네트워크 I/O로 검증한다.
 *
 * 주의: 이 테스트는 live-s3 태그로 분리되어 일반 test 태스크에서는 제외되며,
 * PM의 Docker 실물 검증 환경에서 `./gradlew liveS3Test`로 실행된다.
 * 환경 부재 시 조용히 통과(skip)하지 않고 즉각 실패하도록 설계되었다.
 */
@Tag("live-s3")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class MinIOLiveS3IntegrationTest {

    private val endpoint = System.getenv("MINIO_ENDPOINT")
        ?: System.getProperty("minio.endpoint")
        ?: "http://127.0.0.1:9000"

    private val accessKey = System.getenv("MINIO_ACCESS_KEY")
        ?: System.getProperty("minio.access-key")
        ?: "minioadmin"

    private val secretKey = System.getenv("MINIO_SECRET_KEY")
        ?: System.getProperty("minio.secret-key")
        ?: "minioadmin"

    private val bucket = System.getenv("MINIO_BUCKET")
        ?: System.getProperty("minio.bucket")
        ?: "test-bucket"

    private val region = System.getenv("MINIO_REGION")
        ?: System.getProperty("minio.region")
        ?: "us-east-1"

    private lateinit var s3AsyncClient: S3AsyncClient
    private lateinit var s3Presigner: S3Presigner
    private lateinit var s3StorageAdapter: S3FileStorageAdapter
    private lateinit var s3PresignAdapter: S3FilePresignAdapter
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    @BeforeAll
    fun setup() {
        val credentials = AwsBasicCredentials.create(accessKey, secretKey)
        val credentialsProvider = StaticCredentialsProvider.create(credentials)

        s3AsyncClient = S3AsyncClient.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider)
            .forcePathStyle(true)
            .build()

        s3Presigner = S3Presigner.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider)
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build()
            )
            .build()

        s3StorageAdapter = S3FileStorageAdapter(s3AsyncClient, bucket)
        s3PresignAdapter = S3FilePresignAdapter(s3Presigner, bucket)

        // 버킷 존재 여부 확인 후 미존재 시 생성 (MinIO 연결 검증)
        try {
            s3AsyncClient.headBucket(HeadBucketRequest.builder().bucket(bucket).build()).join()
        } catch (e: Exception) {
            try {
                s3AsyncClient.createBucket(CreateBucketRequest.builder().bucket(bucket).build()).join()
            } catch (createEx: Exception) {
                // 이미 존재하거나 동시 생성된 경우 무시
            }
        }
    }

    @AfterAll
    fun teardown() {
        if (::s3AsyncClient.isInitialized) {
            s3AsyncClient.close()
        }
        if (::s3Presigner.isInitialized) {
            s3Presigner.close()
        }
    }

    private fun sha256Hex(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }

    private class ByteArrayChunkReader(private val bytes: ByteArray) : ChunkReader {
        private var offset = 0

        override suspend fun readChunk(buffer: ByteArray): Int {
            if (offset >= bytes.size) return -1
            val toRead = minOf(buffer.size, bytes.size - offset)
            System.arraycopy(bytes, offset, buffer, 0, toRead)
            offset += toRead
            return toRead
        }

        override suspend fun cancel(cause: Throwable?) {}
        override fun close() {}
    }

    @Test
    @Order(1)
    fun `직접 Presigned PUT 업로드 후 S3 HEAD 메타데이터 기반 Finalize 성공 경로 검증`() = runBlocking {
        val key = UUID.randomUUID().toString()
        val content = "MinIO 실물 검증용 테스트 페이로드입니다. Presigned PUT 업로드 및 Finalize 정합성을 테스트합니다."
        val bytes = content.toByteArray(Charsets.UTF_8)
        val expectedSize = bytes.size.toLong()
        val expectedContentType = "text/plain; charset=UTF-8"
        val expectedChecksum = sha256Hex(bytes)

        // 1. Presigned PUT 요청 명세 발급
        val presignedUpload = s3PresignAdapter.presignUpload(
            key = key,
            expirationSeconds = 120,
            expectedSize = expectedSize,
            contentType = expectedContentType,
            expectedChecksum = expectedChecksum,
        )

        assertEquals("PUT", presignedUpload.method)
        assertNotNull(presignedUpload.url)

        // 2. HTTP 클라이언트를 통해 MinIO에 직접 PUT 업로드 수행
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(presignedUpload.url))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))

        presignedUpload.requiredHeaders.forEach { (name, value) ->
            if (!name.equals("Host", ignoreCase = true)) {
                requestBuilder.header(name, value)
            }
        }

        val uploadResponse = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
        assertTrue(
            uploadResponse.statusCode() in 200..299,
            "MinIO Presigned PUT 업로드가 실패했습니다. HTTP 상태 코드: ${uploadResponse.statusCode()}"
        )

        // 3. Finalize 사후 검증: S3 HEAD 조회
        val metadata = s3StorageAdapter.head(key)
        assertNotNull(metadata, "업로드된 객체의 S3 HEAD 결과가 null이 아니어야 합니다.")
        assertEquals(expectedSize, metadata!!.sizeBytes, "업로드된 객체의 크기가 일치해야 합니다.")
        assertTrue(
            metadata.contentType?.startsWith("text/plain") == true,
            "Content-Type이 일치해야 합니다. 실제: ${metadata.contentType}"
        )
        assertEquals(expectedChecksum, metadata.checksum, "x-amz-meta-sha256 체크섬이 일치해야 합니다.")

        // 4. Finalize 사후 검증 조건 일치 확인 -> READY 전이 가능 상태 검증
        val isFinalizeValid = (metadata.sizeBytes == expectedSize) && (metadata.checksum == expectedChecksum)
        assertTrue(isFinalizeValid, "HEAD 메타데이터 검증을 통과하여 READY 상태로 전이되어야 합니다.")

        // 5. Presigned GET 발급 및 다운로드 데이터 무결성 검증
        val presignedDownload = s3PresignAdapter.presignDownload(key, 60)
        assertEquals("GET", presignedDownload.method)

        val downloadRequest = HttpRequest.newBuilder()
            .uri(URI.create(presignedDownload.url))
            .GET()
            .build()
        val downloadResponse = httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, downloadResponse.statusCode())
        assertArrayEquals(bytes, downloadResponse.body(), "다운로드한 바이트가 업로드한 데이터와 완벽히 일치해야 합니다.")

        // 6. 객체 삭제 및 정리 확인
        val deleted = s3StorageAdapter.delete(key)
        assertTrue(deleted)
        assertFalse(s3StorageAdapter.exists(key), "삭제 후 객체가 존재하지 않아야 합니다.")
    }

    @Test
    @Order(2)
    fun `크기 불일치 시 Finalize 사후 검증 실패 및 객체 정리(delete) 검증`() = runBlocking {
        val key = UUID.randomUUID().toString()
        val actualBytes = "실제 30바이트 미만의 짧은 텍스트".toByteArray(Charsets.UTF_8)
        val actualChecksum = sha256Hex(actualBytes)
        val claimedSize = actualBytes.size.toLong() + 9999L // 클라이언트가 거짓 크기 주장

        // 1. 실제 바이트로 업로드 (서명 시에는 Content-Length 없이 발급)
        val presignedUpload = s3PresignAdapter.presignUpload(
            key = key,
            expirationSeconds = 60,
            expectedSize = null,
            contentType = "application/octet-stream",
            expectedChecksum = actualChecksum,
        )

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(presignedUpload.url))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(actualBytes))

        presignedUpload.requiredHeaders.forEach { (name, value) ->
            if (!name.equals("Host", ignoreCase = true)) {
                requestBuilder.header(name, value)
            }
        }
        val uploadResponse = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
        assertTrue(uploadResponse.statusCode() in 200..299)

        // 2. Finalize 사후 검증: S3 HEAD를 통한 실제 크기 대조
        val metadata = s3StorageAdapter.head(key)
        assertNotNull(metadata)

        val isSizeMatch = metadata!!.sizeBytes == claimedSize
        assertFalse(isSizeMatch, "클라이언트 주장 크기와 실제 S3 객체 크기가 불일치함을 감지해야 합니다.")

        // 3. 불일치 감지 시 정합성 정책: DB FAILED 전이 및 S3 객체 즉시 삭제 정리
        if (!isSizeMatch) {
            val cleanupSuccess = s3StorageAdapter.delete(key)
            assertTrue(cleanupSuccess)
        }

        assertFalse(s3StorageAdapter.exists(key), "불일치로 인해 정리된 객체는 S3에 남아있지 않아야 합니다.")
    }

    @Test
    @Order(3)
    fun `체크섬 불일치 시 Finalize 사후 검증 실패 및 객체 정리 검증`() = runBlocking {
        val key = UUID.randomUUID().toString()
        val actualBytes = "체크섬 불일치 테스트 데이터".toByteArray(Charsets.UTF_8)
        val fakeChecksum = "0000000000000000000000000000000000000000000000000000000000000000"
        val actualChecksum = sha256Hex(actualBytes)

        // 1. 실제 바이트 및 실제 체크섬으로 업로드
        val presignedUpload = s3PresignAdapter.presignUpload(
            key = key,
            expirationSeconds = 60,
            expectedSize = actualBytes.size.toLong(),
            contentType = "text/plain",
            expectedChecksum = actualChecksum,
        )

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(presignedUpload.url))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(actualBytes))

        presignedUpload.requiredHeaders.forEach { (name, value) ->
            if (!name.equals("Host", ignoreCase = true)) {
                requestBuilder.header(name, value)
            }
        }
        val uploadResponse = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
        assertTrue(uploadResponse.statusCode() in 200..299)

        // 2. Finalize 사후 검증: 기대 체크섬(fakeChecksum)과 S3 메타데이터 대조
        val metadata = s3StorageAdapter.head(key)
        assertNotNull(metadata)

        val isChecksumMatch = metadata!!.checksum == fakeChecksum
        assertFalse(isChecksumMatch, "위조된 체크섬과 실제 S3 객체 메타데이터 체크섬이 불일치함을 감지해야 합니다.")

        // 3. 불일치 시 객체 정리
        if (!isChecksumMatch) {
            s3StorageAdapter.delete(key)
        }

        assertFalse(s3StorageAdapter.exists(key), "체크섬 불일치로 정리된 객체는 S3에 남아있지 않아야 합니다.")
    }

    @Test
    @Order(4)
    fun `미업로드 또는 존재하지 않는 객체에 대한 Finalize 시도 시 HEAD null 반환 및 실패 검증`() = runBlocking {
        val nonExistentKey = UUID.randomUUID().toString()

        // 객체를 업로드하지 않고 HEAD 조회
        val metadata = s3StorageAdapter.head(nonExistentKey)
        assertNull(metadata, "존재하지 않는 키의 S3 HEAD 결과는 null이어야 합니다.")
        assertFalse(s3StorageAdapter.exists(nonExistentKey))
    }

    @Test
    @Order(5)
    fun `S3FileStorageAdapter 스트리밍 store, load, delete 왕복 검증`() = runBlocking {
        val key = UUID.randomUUID().toString()
        val data = "Direct S3 Stream store/load test via S3FileStorageAdapter".toByteArray(Charsets.UTF_8)
        val reader = ByteArrayChunkReader(data)

        // 1. store
        val storedFile = s3StorageAdapter.store(
            key = key,
            reader = reader,
            knownSize = data.size.toLong(),
            contentType = "text/plain",
            expectedChecksum = sha256Hex(data),
        )
        assertEquals(data.size.toLong(), storedFile.sizeBytes)

        // 2. exists & head
        assertTrue(s3StorageAdapter.exists(key))
        val meta = s3StorageAdapter.head(key)
        assertNotNull(meta)
        assertEquals(data.size.toLong(), meta!!.sizeBytes)

        // 3. load
        val loadedReader = s3StorageAdapter.load(key)
        assertNotNull(loadedReader)
        val buffer = ByteArray(1024)
        val out = ByteArrayOutputStream()
        var read: Int
        while (loadedReader!!.readChunk(buffer).also { read = it } != -1) {
            out.write(buffer, 0, read)
        }
        loadedReader.close()
        assertArrayEquals(data, out.toByteArray())

        // 4. delete
        assertTrue(s3StorageAdapter.delete(key))
        assertFalse(s3StorageAdapter.exists(key))
        // delete 멱등성 검증 (이미 없는 키 삭제 시도도 성공 반환)
        assertTrue(s3StorageAdapter.delete(key))
    }
}
