package cc.midolog.web.handler

import cc.midolog.infra.security.JwtProvider
import cc.midolog.web.auth.AuthController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * [GlobalExceptionHandler]가 [org.springframework.web.server.ResponseStatusException]의
 * 상태 코드를 보존하는지 `@WebFluxTest` 슬라이스로 검증한다. SecurityConfig는 로드하지
 * 않으므로 인증 여부와 무관하게 요청 본문 바인딩 단계의 실패만 검증한다.
 */
@WebFluxTest(controllers = [AuthController::class])
@Import(GlobalExceptionHandler::class, JwtProvider::class)
@TestPropertySource(
    properties = [
        "jwt.secret=0123456789abcdef0123456789abcdef-strong-random-secret",
        "demo.auth.username=demo-user",
        "demo.auth.password=demo-pass",
    ],
)
class GlobalExceptionHandlerTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `malformed JSON body returns 400 with the ApiResponse envelope`() {
        webTestClient.post().uri("/api/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{ not-valid-json")
            .exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
            .jsonPath("$.code").isEqualTo("INVALID_INPUT")
    }

    @Test
    fun `unsupported content type returns 415 with the ApiResponse envelope`() {
        webTestClient.post().uri("/api/auth/token")
            .contentType(MediaType.TEXT_PLAIN)
            .bodyValue("username=demo-user&password=demo-pass")
            .exchange()
            .expectStatus().isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .expectBody()
            .jsonPath("$.success").isEqualTo(false)
    }
}
