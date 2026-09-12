package cc.midolog.common.security

import cc.midolog.web.auth.AuthController
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * `demo.auth.*` property가 설정되지 않은(빈 문자열 기본값) 상태에서, 형식이
 * 올바른 토큰 발급 요청도 발급되지 않고 401로 실패함을 검증한다(fail-closed).
 * 자격 증명 property를 아예 지정하지 않아 [AuthController]의 기본값(빈 문자열)이
 * 적용되는 상황을 재현한다.
 */
@WebFluxTest(controllers = [AuthController::class])
@Import(SecurityConfig::class, JwtProvider::class)

class AuthFailClosedTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val process = ProcessBuilder("openssl", "rand", "-base64", "48").start()
            val secret = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()
            check(exitCode == 0 && secret.isNotBlank()) { "openssl command failed" }
            registry.add("jwt.secret") { secret }
        }
    }

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

    @Autowired
    lateinit var jwtProvider: JwtProvider

    @Test
    fun `internal gateway requests require authentication`() {
        // Without JWT -> 401
        webTestClient.get().uri("/internal/gateway/requests")
            .exchange()
            .expectStatus().isUnauthorized

        // With valid JWT -> proceeds to handler (returns 404 since handler is absent in this slice test)
        val token = jwtProvider.issue("test-user")
        webTestClient.get().uri("/internal/gateway/requests")
            .header(org.springframework.http.HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isNotFound
    }
}
