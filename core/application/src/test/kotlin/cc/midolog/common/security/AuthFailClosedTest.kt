package cc.midolog.common.security

import cc.midolog.web.auth.AuthController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * `demo.auth.*` property가 설정되지 않은(빈 문자열 기본값) 상태에서, 형식이
 * 올바른 토큰 발급 요청도 발급되지 않고 401로 실패함을 검증한다(fail-closed).
 * 자격 증명 property를 아예 지정하지 않아 [AuthController]의 기본값(빈 문자열)이
 * 적용되는 상황을 재현한다.
 */
@WebFluxTest(controllers = [AuthController::class])
@Import(SecurityConfig::class, JwtProvider::class)
@TestPropertySource(
    properties = [
        "jwt.secret=0123456789abcdef0123456789abcdef-strong-random-secret",
    ],
)
class AuthFailClosedTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `token issuance fails closed when demo credentials are not configured`() {
        webTestClient.post().uri("/api/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("username" to "demo-user", "password" to "demo-pass"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `token issuance fails closed even with blank credential values configured`() {
        webTestClient.post().uri("/api/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("username" to "", "password" to ""))
            .exchange()
            .expectStatus().isUnauthorized
    }
}
