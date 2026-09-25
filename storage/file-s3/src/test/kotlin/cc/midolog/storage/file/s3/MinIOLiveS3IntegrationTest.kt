package cc.midolog.storage.file.s3

import cc.midolog.business.service.FileService
import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.PresignedRequest
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.ChunkReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * MinIO 실물 컨테이너 환경을 대상으로 하는 S3 엔드투엔드 통합 테스트.
 *
 * FileService 서비스 레이어와 S3 어댑터, 영속성 포트를 직접 연결하여
 * Presigned PUT 업로드, finalizeUpload의 상태 전이(READY, FAILED),
 * 동시 finalize/delete 경쟁 방어, 체크섬 무결성 검증, 인가 검증 등을 실제 MinIO I/O로 검증한다.
 *
 * 주의: 이 테스트는 live-s3 태그로 분리되어 일반 test 태스크에서는 제외되며,
 * PM의 Docker 실물 검증 환경에서 `./gradlew liveS3Test`로 실행된다.
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
        ?: "app-test-user"

    private val secretKey = System.getenv("MINIO_SECRET_KEY")
        ?: System.getProperty("minio.secret-key")
        ?: "app-test-password"

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
    private lateinit var fileMetaRepository: InMemoryFileMetaRepository
    private lateinit var fileService: FileService

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
        fileMetaRepository = InMemoryFileMetaRepository()
        fileService = FileService(
            clock = Clock.systemUTC(),
            fileStoragePort = s3StorageAdapter,
            fileMetaRepositoryPort = fileMetaRepository,
            filePresignPort = Optional.of(s3PresignAdapter),
        )

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

    private fun uploadViaPresignedPut(presigned: PresignedRequest, bytes: ByteArray): HttpResponse<Void> {
        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(presigned.url))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))

        presigned.requiredHeaders.forEach { (name, value) ->
            if (!name.equals("Host", ignoreCase = true) && !name.equals("Content-Length", ignoreCase = true)) {
                requestBuilder.header(name, value)
            }
        }

        return httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding())
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
    fun `FileService presign 및 업로드 후 finalizeUpload 성공 경로와 상태 전이 검증`() = runBlocking {
        val ownerId = "user-${UUID.randomUUID()}"
        val content = "MinIO 실물 검증용 테스트 페이로드입니다. FileService.finalizeUpload 정합성을 테스트합니다."
        val bytes = content.toByteArray(Charsets.UTF_8)
        val expectedSize = bytes.size.toLong()
        val expectedContentType = "text/plain; charset=UTF-8"
        val expectedChecksum = sha256Hex(bytes)

        // 1. FileService를 통해 사전 서명 URL 발급 및 PENDING 메타데이터 생성
        val (pendingMeta, presignedUpload) = fileService.presignUpload(
            ownerId = ownerId,
            contentType = expectedContentType,
            expectedSize = expectedSize,
            expectedChecksum = expectedChecksum,
            expirationSeconds = 120,
        )
        val fileId = pendingMeta.id

        // DB 메타데이터가 PENDING 상태인지 확인
        val currentPending = fileMetaRepository.findById(fileId)
        assertNotNull(currentPending)
        assertEquals(FileStatus.PENDING, currentPending!!.status)

        // 2. HTTP 클라이언트를 통해 MinIO에 직접 PUT 업로드 수행 (Content-Length 수동 설정 배제)
        val uploadResponse = uploadViaPresignedPut(presignedUpload, bytes)
        assertTrue(
            uploadResponse.statusCode() in 200..299,
            "MinIO Presigned PUT 업로드가 실패했습니다. HTTP 상태 코드: ${uploadResponse.statusCode()}"
        )

        // 3. 실제 FileService.finalizeUpload 호출을 통한 서비스 레벨 사후 검증 및 상태 전이
        val finalizedFile = fileService.finalizeUpload(
            id = fileId,
            ownerId = ownerId,
            maxSizeBytes = 10 * 1024 * 1024L,
        )
        assertNotNull(finalizedFile)
        assertEquals(FileStatus.READY, finalizedFile.status, "업로드 완료 후 파일 상태는 READY여야 합니다.")
        assertEquals(expectedSize, finalizedFile.sizeBytes)
        assertEquals(expectedContentType, finalizedFile.contentType)

        // 4. DB 영속 상태 재확인
        val readyMeta = fileMetaRepository.findById(fileId)
        assertNotNull(readyMeta)
        assertEquals(FileStatus.READY, readyMeta!!.status)
        assertEquals(expectedChecksum, readyMeta.checksum)

        // 5. Presigned GET 발급 및 다운로드 데이터 무결성 검증
        val presignedDownload = s3PresignAdapter.presignDownload(readyMeta.storageKey, 60)
        assertEquals("GET", presignedDownload.method)

        val downloadRequest = HttpRequest.newBuilder()
            .uri(URI.create(presignedDownload.url))
            .GET()
            .build()
        val downloadResponse = httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, downloadResponse.statusCode())
        assertArrayEquals(bytes, downloadResponse.body(), "다운로드한 바이트가 업로드한 데이터와 완벽히 일치해야 합니다.")

        // 6. FileService.deleteFile 호출 및 정리 확인
        fileService.deleteFile(id = fileId, ownerId = ownerId)
        val deletedMeta = fileMetaRepository.findById(fileId)
        assertEquals(FileStatus.DELETED, deletedMeta?.status)
        assertFalse(s3StorageAdapter.exists(readyMeta.storageKey), "삭제 후 S3 객체가 정리되어야 합니다.")
    }

    @Test
    @Order(2)
    fun `서명된 Content-Length와 불일치 시 S3 업로드 조기 거부 및 객체 미생성 검증`() = runBlocking {
        val ownerId = "user-${UUID.randomUUID()}"
        val actualBytes = "실제 30바이트 미만의 짧은 텍스트".toByteArray(Charsets.UTF_8)
        val actualChecksum = sha256Hex(actualBytes)
        val claimedSize = actualBytes.size.toLong() + 9999L // 클라이언트가 거짓 크기 주장

        // 1. 거짓 크기로 FileService presignUpload 호출 (URL 서명에 Content-Length 바인딩)
        val (pendingMeta, presignedUpload) = fileService.presignUpload(
            ownerId = ownerId,
            contentType = "application/octet-stream",
            expectedSize = claimedSize,
            expectedChecksum = actualChecksum,
            expirationSeconds = 60,
        )

        // 2. 실제 바이트로 S3 업로드 시도 -> 서명된 Content-Length 불일치로 S3 계층에서 조기 거부(4xx)
        val uploadResponse = uploadViaPresignedPut(presignedUpload, actualBytes)
        assertTrue(
            uploadResponse.statusCode() in 400..499,
            "서명된 Content-Length와 다른 크기의 업로드는 S3(MinIO) 계층에서 조기 거부(4xx)되어야 합니다. 실제 상태 코드: ${uploadResponse.statusCode()}",
        )

        // 3. 거부된 업로드 객체는 S3에 생성되지 않아야 함
        assertFalse(s3StorageAdapter.exists(pendingMeta.storageKey), "조기 거부된 업로드 객체는 S3에 존재하지 않아야 합니다.")

        // 4. DB 상태는 변경 없이 PENDING으로 안전하게 유지
        val meta = fileMetaRepository.findById(pendingMeta.id)
        assertNotNull(meta)
        assertEquals(FileStatus.PENDING, meta!!.status)
    }

    @Test
    @Order(3)
    fun `Finalize 단계의 HEAD 크기 불일치 시 FileService finalizeUpload 실패 및 FAILED 상태 전이와 S3 객체 물리 삭제 검증`() = runBlocking {
        val ownerId = "user-${UUID.randomUUID()}"
        val actualBytes = "실제 정상 크기 데이터입니다.".toByteArray(Charsets.UTF_8)
        val actualSize = actualBytes.size.toLong()
        val actualChecksum = sha256Hex(actualBytes)

        // 1. 정상 크기로 presignUpload 발급 및 실제 업로드 성공
        val (pendingMeta, presignedUpload) = fileService.presignUpload(
            ownerId = ownerId,
            contentType = "application/octet-stream",
            expectedSize = actualSize,
            expectedChecksum = actualChecksum,
            expirationSeconds = 60,
        )
        val uploadResponse = uploadViaPresignedPut(presignedUpload, actualBytes)
        assertTrue(uploadResponse.statusCode() in 200..299, "최초 정상 업로드는 성공해야 합니다.")
        assertTrue(s3StorageAdapter.exists(pendingMeta.storageKey), "S3에 객체가 생성되어야 합니다.")

        // 2. DB FileMeta의 sizeBytes를 실제 업로드 크기와 다르게 변조하여 Finalize 시점의 HEAD 크기 불일치 조건(FileService 167행) 재현
        val tamperedMeta = pendingMeta.copy(sizeBytes = actualSize + 1000L)
        fileMetaRepository.save(tamperedMeta)

        // 3. FileService.finalizeUpload 호출 시 크기 불일치 감지 및 예외 발생 확인
        val ex = assertThrows(Exception::class.java) {
            runBlocking {
                fileService.finalizeUpload(
                    id = pendingMeta.id,
                    ownerId = ownerId,
                    maxSizeBytes = 10 * 1024 * 1024L,
                )
            }
        }
        assertTrue(ex.message?.contains("size") == true || ex.message?.contains("크기") == true)

        // 4. DB 상태가 FAILED로 전이되었는지 확인
        val failedMeta = fileMetaRepository.findById(pendingMeta.id)
        assertNotNull(failedMeta)
        assertEquals(FileStatus.FAILED, failedMeta!!.status, "크기 불일치 시 DB 상태는 FAILED여야 합니다.")

        // 5. failAndCleanup에 의해 S3 객체가 즉시 물리 삭제되었는지 확인
        assertFalse(s3StorageAdapter.exists(failedMeta.storageKey), "검증 실패한 객체는 S3에서 물리 삭제되어야 합니다.")
    }

    @Test
    @Order(4)
    fun `체크섬 불일치 시 FileService finalizeUpload 실패 및 FAILED 상태 전이와 S3 객체 물리 삭제 검증`() = runBlocking {
        val ownerId = "user-${UUID.randomUUID()}"
        val actualBytes = "체크섬 불일치 테스트 데이터".toByteArray(Charsets.UTF_8)
        val fakeChecksum = "0000000000000000000000000000000000000000000000000000000000000000"

        // 1. 위조된 체크섬으로 presignUpload 발급
        val (pendingMeta, presignedUpload) = fileService.presignUpload(
            ownerId = ownerId,
            contentType = "text/plain",
            expectedSize = actualBytes.size.toLong(),
            expectedChecksum = fakeChecksum,
            expirationSeconds = 60,
        )
        val fileId = pendingMeta.id

        // 2. 실제 바이트 업로드
        val uploadResponse = uploadViaPresignedPut(presignedUpload, actualBytes)
        // 만약 S3 Native Checksum 헤더 검증으로 400 반환되었거나 200 업로드되었더라도 finalize 단계에서 잡혀야 함
        if (uploadResponse.statusCode() in 200..299) {
            // 3. finalizeUpload 호출 시 체크섬 불일치로 실패 검증
            val ex = assertThrows(Exception::class.java) {
                runBlocking {
                    fileService.finalizeUpload(
                        id = fileId,
                        ownerId = ownerId,
                        maxSizeBytes = 10 * 1024 * 1024L,
                    )
                }
            }
            assertTrue(ex.message?.contains("checksum") == true || ex.message?.contains("체크섬") == true)

            // 4. DB 상태 FAILED 및 S3 객체 삭제 검증
            val failedMeta = fileMetaRepository.findById(fileId)
            assertNotNull(failedMeta)
            assertEquals(FileStatus.FAILED, failedMeta!!.status)
            assertFalse(s3StorageAdapter.exists(failedMeta.storageKey), "체크섬 불일치 객체는 S3에서 삭제되어야 합니다.")
        }
    }

    @Test
    @Order(5)
    fun `미업로드 객체에 대한 FileService finalizeUpload 시 FAILED 전이 및 예외 발생 검증`() = runBlocking {
        val ownerId = "user-${UUID.randomUUID()}"
        val (pendingMeta, _) = fileService.presignUpload(
            ownerId = ownerId,
            contentType = "text/plain",
            expectedSize = 100,
            expectedChecksum = null,
            expirationSeconds = 60,
        )
        val fileId = pendingMeta.id

        // 업로드 없이 바로 finalizeUpload 시도
        val ex = assertThrows(Exception::class.java) {
            runBlocking {
                fileService.finalizeUpload(
                    id = fileId,
                    ownerId = ownerId,
                    maxSizeBytes = 10 * 1024 * 1024L,
                )
            }
        }
        assertTrue(ex.message?.contains("exist") == true || ex.message?.contains("존재") == true)

        val meta = fileMetaRepository.findById(fileId)
        assertNotNull(meta)
        assertEquals(FileStatus.FAILED, meta!!.status)
    }

    @Test
    @Order(6)
    fun `타 소유자 파일에 대한 접근 시 인가 거부 검증`() = runBlocking {
        val owner1 = "user-alpha-${UUID.randomUUID()}"
        val owner2 = "user-beta-${UUID.randomUUID()}"
        val bytes = "인가 테스트".toByteArray(Charsets.UTF_8)

        val (pendingMeta, presigned) = fileService.presignUpload(
            ownerId = owner1,
            contentType = "text/plain",
            expectedSize = bytes.size.toLong(),
            expectedChecksum = sha256Hex(bytes),
            expirationSeconds = 60,
        )
        val fileId = pendingMeta.id
        uploadViaPresignedPut(presigned, bytes)

        // owner2가 finalizeUpload 시도 시 404/거부 예외 발생
        assertThrows(Exception::class.java) {
            runBlocking {
                fileService.finalizeUpload(
                    id = fileId,
                    ownerId = owner2,
                    maxSizeBytes = 10 * 1024 * 1024L,
                )
            }
        }

        // owner1의 파일 상태는 여전히 안전하게 PENDING으로 보호됨
        val meta = fileMetaRepository.findById(fileId)
        assertEquals(FileStatus.PENDING, meta?.status)

        // 정상 owner1이 finalize 수행
        val finalized = fileService.finalizeUpload(
            id = fileId,
            ownerId = owner1,
            maxSizeBytes = 10 * 1024 * 1024L,
        )
        assertEquals(FileStatus.READY, finalized.status)

        // owner2가 getFile 시도 시 거부
        assertThrows(Exception::class.java) {
            runBlocking {
                fileService.getFile(id = fileId, ownerId = owner2)
            }
        }
    }

    @Test
    @Order(7)
    fun `READY 상태 파일에 대한 finalizeUpload 중복 호출 시 멱등 성공 검증`() = runBlocking {
        val ownerId = "user-${UUID.randomUUID()}"
        val bytes = "중복 finalize 멱등 테스트".toByteArray(Charsets.UTF_8)
        val (pendingMeta, presigned) = fileService.presignUpload(
            ownerId = ownerId,
            contentType = "text/plain",
            expectedSize = bytes.size.toLong(),
            expectedChecksum = sha256Hex(bytes),
            expirationSeconds = 60,
        )
        val fileId = pendingMeta.id
        uploadViaPresignedPut(presigned, bytes)

        val firstFinalize = fileService.finalizeUpload(
            id = fileId,
            ownerId = ownerId,
            maxSizeBytes = 10 * 1024 * 1024L,
        )
        assertEquals(FileStatus.READY, firstFinalize.status)

        // 두 번째 finalize 호출 -> 예외 없이 기존 READY 파일 멱등 반환
        val secondFinalize = fileService.finalizeUpload(
            id = fileId,
            ownerId = ownerId,
            maxSizeBytes = 10 * 1024 * 1024L,
        )
        assertEquals(FileStatus.READY, secondFinalize.status)
        assertEquals(firstFinalize.id, secondFinalize.id)
    }

    @Test
    @Order(8)
    fun `동일 presigned URL 재사용 시 If-None-Match 조건에 의해 S3 객체 덮어쓰기 차단 검증 (expectedChecksum null 포함)`() = runBlocking {
        val ownerId = "user-${UUID.randomUUID()}"
        val initialData = "최초 업로드된 원본 데이터입니다.".toByteArray(Charsets.UTF_8)
        val overwriteData = "악의적으로 덮어쓰려는 변조 데이터입니다.".toByteArray(Charsets.UTF_8)

        // 1. expectedChecksum이 null인 상태에서 presigned URL 발급
        val (pendingMeta, presignedUpload) = fileService.presignUpload(
            ownerId = ownerId,
            contentType = "text/plain",
            expectedSize = initialData.size.toLong(),
            expectedChecksum = null, // 체크섬이 null인 경로
            expirationSeconds = 120,
        )
        val fileId = pendingMeta.id

        // 2. 최초 정상 업로드 성공 (If-None-Match: * 헤더 포함)
        val firstUploadResponse = uploadViaPresignedPut(presignedUpload, initialData)
        assertTrue(firstUploadResponse.statusCode() in 200..299, "최초 업로드는 성공해야 합니다.")

        // 3. finalizeUpload 성공 확인 (상태 READY)
        val finalizedFile = fileService.finalizeUpload(
            id = fileId,
            ownerId = ownerId,
            maxSizeBytes = 10 * 1024 * 1024L,
        )
        assertEquals(FileStatus.READY, finalizedFile.status)

        // 4. 중복 finalizeUpload 멱등 성공 유지 확인
        val idempotentFinalize = fileService.finalizeUpload(
            id = fileId,
            ownerId = ownerId,
            maxSizeBytes = 10 * 1024 * 1024L,
        )
        assertEquals(FileStatus.READY, idempotentFinalize.status)

        // 5. 동일한 presignedUpload URL로 다른 바이트(overwriteData) 덮어쓰기 재업로드 시도
        // S3 네이티브 If-None-Match: * 조건에 의해 객체가 이미 존재하므로 412 Precondition Failed (또는 4xx)로 거부되어야 함
        val overwriteResponse = uploadViaPresignedPut(presignedUpload, overwriteData)
        assertTrue(
            overwriteResponse.statusCode() in 400..499,
            "이미 객체가 존재하는 상태에서 presigned URL 재업로드는 S3(MinIO)에서 조기 차단(4xx)되어야 합니다. HTTP 상태 코드: ${overwriteResponse.statusCode()}",
        )

        // 6. S3에 저장된 객체가 덮어써지지 않고 최초 initialData로 온전히 보존되어 있는지 검증
        val presignedDownload = s3PresignAdapter.presignDownload(finalizedFile.storageKey, 60)
        val downloadRequest = HttpRequest.newBuilder()
            .uri(URI.create(presignedDownload.url))
            .GET()
            .build()
        val downloadResponse = httpClient.send(downloadRequest, HttpResponse.BodyHandlers.ofByteArray())
        assertEquals(200, downloadResponse.statusCode())
        assertArrayEquals(initialData, downloadResponse.body(), "S3 객체는 덮어써지지 않고 최초 데이터로 보존되어야 합니다.")

        // 7. 정리
        fileService.deleteFile(id = fileId, ownerId = ownerId)
    }

    @Test
    @Order(9)
    fun `동시 finalizeUpload와 deleteFile 경쟁 시 DELETED 상태 보장 및 READY 부활 차단 검증`() = runBlocking {
        val ownerId = "user-${UUID.randomUUID()}"
        val bytes = "동시 finalize/delete 경쟁 테스트 데이터".toByteArray(Charsets.UTF_8)
        val (pendingMeta, presigned) = fileService.presignUpload(
            ownerId = ownerId,
            contentType = "text/plain",
            expectedSize = bytes.size.toLong(),
            expectedChecksum = sha256Hex(bytes),
            expirationSeconds = 60,
        )
        val fileId = pendingMeta.id
        uploadViaPresignedPut(presigned, bytes)

        val metaBefore = fileMetaRepository.findById(fileId)!!

        // 거의 동시에 finalize와 delete 실행
        val finalizeJob = async(Dispatchers.IO) {
            try {
                fileService.finalizeUpload(
                    id = fileId,
                    ownerId = ownerId,
                    maxSizeBytes = 10 * 1024 * 1024L,
                )
            } catch (e: Exception) {
                null
            }
        }
        val deleteJob = async(Dispatchers.IO) {
            try {
                fileService.deleteFile(id = fileId, ownerId = ownerId)
            } catch (e: Exception) {
                null
            }
        }

        awaitAll(finalizeJob, deleteJob)

        // 최종 상태 확인: delete가 실행되었으므로 최종 DB 상태는 반드시 DELETED여야 하며,
        // READY로 절대 부활하지 않아야 한다.
        val finalMeta = fileMetaRepository.findById(fileId)
        assertEquals(FileStatus.DELETED, finalMeta?.status, "동시 삭제 경쟁 후 파일 상태는 DELETED여야 합니다.")
        assertFalse(s3StorageAdapter.exists(metaBefore.storageKey), "S3 객체는 삭제되어야 합니다.")
    }

    @Test
    @Order(10)
    fun `S3FileStorageAdapter 스트리밍 store, load, delete 왕복 검증`() = runBlocking {
        val key = UUID.randomUUID().toString()
        val data = "Direct S3 Stream store/load test via S3FileStorageAdapter with disk spooling".toByteArray(Charsets.UTF_8)
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

    /**
     * 동시성 및 상태 전이 원자성을 검증하기 위한 인메모리 영속 포트 구현체.
     */
    private class InMemoryFileMetaRepository : FileMetaRepositoryPort {
        private val storage = ConcurrentHashMap<String, FileMeta>()

        override suspend fun save(file: FileMeta): FileMeta {
            val id = if (file.id.isBlank()) UUID.randomUUID().toString() else file.id
            val updated = file.copy(id = id)
            storage[id] = updated
            return updated
        }

        override suspend fun findById(id: String): FileMeta? = storage[id]

        override suspend fun updateStatus(id: String, status: FileStatus): Boolean {
            val current = storage[id] ?: return false
            storage[id] = current.copy(status = status, updatedAt = Instant.now())
            return true
        }

        override suspend fun updateStatusConditionally(
            id: String,
            expectedStatuses: Set<FileStatus>,
            newStatus: FileStatus,
            sizeBytes: Long?,
            contentType: String?,
            checksum: String?,
        ): Boolean {
            var success = false
            storage.compute(id) { _, current ->
                if (current != null && current.status in expectedStatuses) {
                    success = true
                    current.copy(
                        status = newStatus,
                        sizeBytes = sizeBytes ?: current.sizeBytes,
                        contentType = contentType ?: current.contentType,
                        checksum = checksum ?: current.checksum,
                        updatedAt = Instant.now(),
                    )
                } else {
                    current
                }
            }
            return success
        }

        override suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta> {
            return findExpiredOrphans(cutoff, limit, setOf(FileStatus.PENDING))
        }

        override suspend fun findExpiredOrphans(
            cutoff: Instant,
            limit: Int,
            statuses: Set<FileStatus>,
        ): List<FileMeta> {
            return storage.values
                .filter { it.status in statuses && it.updatedAt < cutoff }
                .take(limit)
        }
    }
}
