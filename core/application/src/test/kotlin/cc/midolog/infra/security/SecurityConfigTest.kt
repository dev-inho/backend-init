package cc.midolog.infra.security

import cc.midolog.web.auth.AuthController
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * Application 자체 보안 설정 검증 (Application 8081 직접 접근 모델).
 *
 * `@WebFluxTest` 슬라이스로 [AuthController]와 [SecurityConfig]만 로드해
 * DataSource/MyBatis/Redis 등 실제 인프라 의존 없이 인증 동작을 검증한다.
 */
@WebFluxTest(controllers = [AuthController::class])
@Import(SecurityConfig::class, JwtProvider::class)
@TestPropertySource(
    properties = [
        "jwt.secret=0123456789abcdef0123456789abcdef-strong-random-secret",
        "demo.auth.username=demo-user",
        "demo.auth.password=demo-pass",
    ],
)
class SecurityConfigTest {

    @Autowired
    lateinit var webTestClient: WebTestClient

    @Test
    fun `direct access to protected api path without a token is rejected`() {
        webTestClient.get().uri("/api/sample/ping")
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `actuator health path is not blocked by authentication`() {
        // 이 슬라이스에는 실제 actuator 핸들러가 등록되지 않으므로 404가 나올 수 있으나,
        // 보안 필터에 의한 401 차단이 없음(익명 허용)을 검증한다.
        val result = webTestClient.get().uri("/actuator/health").exchange().returnResult(Void::class.java)
        assertNotEquals(HttpStatus.UNAUTHORIZED, result.status)
    }

    @Test
    fun `token issuance succeeds when credentials match configured demo credentials`() {
        webTestClient.post().uri("/api/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("username" to "demo-user", "password" to "demo-pass"))
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.data.token").isNotEmpty()
    }

    @Test
    fun `token issuance fails when credentials do not match`() {
        webTestClient.post().uri("/api/auth/token")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue(mapOf("username" to "demo-user", "password" to "wrong-password"))
            .exchange()
            .expectStatus().isUnauthorized
    }

    @Test
    fun `path not explicitly permitted or authenticated is denied by the denyAll fallback`() {
        // /actuator/info는 익명 허용 목록에 없고 /api/** 도 아니므로 anyExchange().denyAll() 폴백에 걸려야 한다.
        webTestClient.get().uri("/actuator/info")
            .exchange()
            .expectStatus().is4xxClientError()
    }

    @Test
    fun `GET to the auth token path is not covered by the anonymous permit and is denied`() {
        // 익명 허용은 POST /api/auth/token 한정이므로 GET은 authenticated()/denyAll 대상이다.
        webTestClient.get().uri("/api/auth/token")
            .exchange()
            .expectStatus().is4xxClientError()
    }
}
