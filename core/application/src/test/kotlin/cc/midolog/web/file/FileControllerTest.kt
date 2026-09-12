package cc.midolog.web.file

import cc.midolog.business.service.FileService
import cc.midolog.common.security.JwtProvider
import cc.midolog.common.security.SecurityConfig
import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.FileMeta
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.web.exception.ApiException
import cc.midolog.web.handler.GlobalExceptionHandler
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import java.time.Instant

object FakeReaderStats {
    var cancelCount = 0
    var closeCount = 0
    fun reset() {
        cancelCount = 0
        closeCount = 0
    }
}

@Configuration
class FileControllerTestConfig {
    @Bean
    fun fakeFileService() = object : FileService(
        fileStoragePort = object : cc.midolog.file.port.storage.FileStoragePort {
            override suspend fun store(k: String, r: ChunkReader, s: Long?, c: String, e: String?) = throw NotImplementedError()
            override suspend fun load(k: String): ChunkReader? = null
            override suspend fun delete(k: String) = true
            override suspend fun exists(k: String) = false
        },
        fileMetaRepositoryPort = object : cc.midolog.file.port.repository.FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = null
            override suspend fun save(file: FileMeta): FileMeta = file
            override suspend fun updateStatus(id: String, status: FileStatus) = true
            override suspend fun findExpiredPending(cutoff: Instant, limit: Int) = emptyList<FileMeta>()
        }
    ) {
        override suspend fun uploadFile(ownerId: String, contentType: String, reader: ChunkReader): FileMeta {
            val buffer = ByteArray(4096)
            var total = 0L
            while (true) {
                val read = reader.readChunk(buffer)
                if (read == -1) break
                total += read
            }
            return FileMeta("id-123", ownerId, "storage-key-123", total, contentType, "checksum", FileStatus.READY, Instant.now(), Instant.now())
        }

        override suspend fun getFile(id: String, ownerId: String): FileMeta {
            if (id == "error-reader") {
                return FileMeta("error-reader", "test-owner", "key-error", 100, "image/png", null, FileStatus.READY, Instant.now(), Instant.now())
            }
            if (id == "123" && ownerId == "test-owner") {
                return FileMeta("id-123", "test-owner", "key-123", 100, "image/png", null, FileStatus.READY, Instant.now(), Instant.now())
            }
            throw ApiException.notFound("file not found")
        }

        override suspend fun deleteFile(id: String, ownerId: String) {
            if (id == "123" && ownerId == "test-owner") {
                return
            }
            throw ApiException.notFound("file not found")
        }

        override suspend fun loadContent(id: String, ownerId: String): ChunkReader {
            if (id == "error-reader") {
                return object : ChunkReader {
                    override suspend fun readChunk(buffer: ByteArray): Int {
                        throw RuntimeException("Reader failed")
                    }
                    override suspend fun cancel(cause: Throwable?) { FakeReaderStats.cancelCount++ }
                    override fun close() { FakeReaderStats.closeCount++ }
                }
            }
            if (id == "123" && ownerId == "test-owner") {
                return object : ChunkReader {
                    var offset = 0
                    val size = 100
                    override suspend fun readChunk(buffer: ByteArray): Int {
                        if (offset >= size) return -1
                        val length = minOf(buffer.size, size - offset)
                        for (i in 0 until length) buffer[i] = (offset + i).toByte()
                        offset += length
                        return length
                    }
                    override suspend fun cancel(cause: Throwable?) { FakeReaderStats.cancelCount++ }
                    override fun close() { FakeReaderStats.closeCount++ }
                }
            }
            throw ApiException.notFound("file not found")
        }
    }
}

@WebFluxTest(controllers = [FileController::class])
@Import(SecurityConfig::class, JwtProvider::class, GlobalExceptionHandler::class, FileControllerTestConfig::class)
@TestPropertySource(
    properties = [
        "jwt.secret=0123456789abcdef0123456789abcdef-strong-random-secret",
        "storage.file.max-size-bytes=8192", // 8KB
        "storage.file.allowed-content-types=image/png,image/jpeg,application/pdf"
    ]
)
class FileControllerTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Autowired
    lateinit var jwtProvider: JwtProvider

    private val ownerId = "test-owner"
    private val validToken by lazy { jwtProvider.issue(ownerId) }

    @Test
    fun `무토큰 접근 시 401 반환`() {
        webTestClient.get().uri("/api/files/123")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `비허용 content-type 업로드 시 415 반환`() {
        val builder = MultipartBodyBuilder()
        builder.part("file", ByteArrayResource(ByteArray(10)), MediaType.TEXT_PLAIN)
            .filename("test.txt")

        webTestClient.post().uri("/api/files")
            .header("Authorization", "Bearer $validToken")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectStatus().isEqualTo(415)
    }

    @Test
    fun `정상 파일 업로드 시 201 반환 및 도메인 객체 노출 방지 확인`() {
        val builder = MultipartBodyBuilder()
        builder.part("file", ByteArrayResource(ByteArray(1000)), MediaType.IMAGE_PNG)
            .filename("image.png")

        webTestClient.post().uri("/api/files")
            .header("Authorization", "Bearer $validToken")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.data.id").isEqualTo("id-123")
            .jsonPath("$.data.ownerId").doesNotExist()
    }

    @Test
    fun `상한 초과 크기 파일 업로드 시 413 반환`() {
        val builder = MultipartBodyBuilder()
        builder.part("file", ByteArrayResource(ByteArray(9000)), MediaType.IMAGE_PNG) // 9000 > 8192
            .filename("large.png")

        webTestClient.post().uri("/api/files")
            .header("Authorization", "Bearer $validToken")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(BodyInserters.fromMultipartData(builder.build()))
            .exchange()
            .expectStatus().isEqualTo(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE)
            .expectBody().consumeWith { println("Response: " + String(it.responseBody ?: ByteArray(0))) }
    }

    @Test
    fun `정상 파일 다운로드 시 content-type, length, 실제 bytes 가드`() {
        FakeReaderStats.reset()
        val result = webTestClient.get().uri("/api/files/123/content")
            .header("Authorization", "Bearer $validToken")
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.IMAGE_PNG)
            .expectHeader().contentLength(100)
            .expectBody().returnResult()

        org.junit.jupiter.api.Assertions.assertEquals(100, result.responseBody?.size)
        org.junit.jupiter.api.Assertions.assertEquals(1, FakeReaderStats.closeCount)
    }

    @Test
    fun `다운로드 중 reader 에러 발생 시 cancel 및 close 가드`() {
        FakeReaderStats.reset()
        webTestClient.get().uri("/api/files/error-reader/content")
            .header("Authorization", "Bearer $validToken")
            .exchange()
            .expectStatus().is5xxServerError

        org.junit.jupiter.api.Assertions.assertEquals(1, FakeReaderStats.cancelCount)
        org.junit.jupiter.api.Assertions.assertEquals(1, FakeReaderStats.closeCount)
    }

    @Test
    fun `파일 삭제 성공`() {
        webTestClient.delete().uri("/api/files/123")
            .header("Authorization", "Bearer $validToken")
            .exchange()
            .expectStatus().isOk
    }

    @Test
    fun `다른 owner가 파일 삭제 시 404 반환`() {
        val otherToken = jwtProvider.issue("other-owner")
        webTestClient.delete().uri("/api/files/123")
            .header("Authorization", "Bearer $otherToken")
            .exchange()
            .expectStatus().isNotFound
    }

    @Test
    fun `다른 owner가 조회 요청 시 404 반환`() {
        val otherToken = jwtProvider.issue("other-owner")
        webTestClient.get().uri("/api/files/123")
            .header("Authorization", "Bearer $otherToken")
            .exchange()
            .expectStatus().isNotFound
    }
}
