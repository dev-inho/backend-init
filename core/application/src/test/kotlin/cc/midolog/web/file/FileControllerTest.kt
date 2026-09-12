package cc.midolog.web.file

import cc.midolog.business.service.FileService
import cc.midolog.common.security.JwtProvider
import cc.midolog.common.security.SecurityConfig
import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.StoredFile
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
            override suspend fun findById(id: String): StoredFile? = null
            override suspend fun save(file: StoredFile): StoredFile = file
            override suspend fun updateStatus(id: String, status: FileStatus) = true
            override suspend fun findExpiredPending(limit: Int) = emptyList<StoredFile>()
        }
    ) {
        override suspend fun uploadFile(ownerId: String, contentType: String, reader: ChunkReader): StoredFile {
            val buffer = ByteArray(4096)
            var total = 0L
            while (true) {
                val read = reader.readChunk(buffer)
                if (read == -1) break
                total += read
            }
            return StoredFile("id-123", ownerId, "storage-key-123", total, contentType, "checksum", FileStatus.READY)
        }

        override suspend fun getFile(id: String, ownerId: String): StoredFile {
            if (id == "123" && ownerId == "test-owner") {
                return StoredFile("id-123", "test-owner", "key-123", 100, "image/png", null, FileStatus.READY)
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
            .jsonPath("$.data.storageKey").isEqualTo("storage-key-123")
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
    fun `다른 owner가 조회 요청 시 404 반환`() {
        val otherToken = jwtProvider.issue("other-owner")

        webTestClient.get().uri("/api/files/123")
            .header("Authorization", "Bearer $otherToken")
            .exchange()
            .expectStatus().isNotFound
    }
}
