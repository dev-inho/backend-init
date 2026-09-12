package cc.midolog

import cc.midolog.common.security.JwtProvider
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.sample.port.cache.SampleCachePort
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.reactive.function.server.MockServerRequest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.support.RouterFunctionMapping
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping
import reactor.test.StepVerifier
import java.nio.file.Path
import org.springframework.web.server.ServerWebExchange

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "gateway.mode=embedded",
        "gateway.request-visibility.enabled=true",
        "spring.datasource.url=jdbc:postgresql://127.0.0.1:65535/test",
        "spring.datasource.username=test",
        "spring.datasource.password=test",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
        "spring.data.redis.repositories.enabled=false",
        "storage.file.provider=local",
        "storage.file.max-size-bytes=8192",
        "storage.file.allowed-content-types=image/png,image/jpeg,application/pdf"
    ]
)
class GatewayEmbeddedIntegrationTest {

    @MockitoBean lateinit var fileMetaRepositoryPort: FileMetaRepositoryPort
    @MockitoBean lateinit var userRepositoryPort: UserRepositoryPort
    @MockitoBean lateinit var sampleRepositoryPort: SampleRepositoryPort
    @MockitoBean lateinit var sampleCachePort: SampleCachePort
    @MockitoBean lateinit var domainFileStoragePort: cc.midolog.file.port.storage.FileStoragePort
    @MockitoBean lateinit var filePresignPort: cc.midolog.file.port.storage.FilePresignPort
    @MockitoBean lateinit var sampleFileStoragePort: cc.midolog.sample.port.file.FileStoragePort

    @Autowired
    lateinit var applicationContext: ApplicationContext

    lateinit var webTestClient: WebTestClient
    
    @Autowired
    lateinit var jwtProvider: JwtProvider

    @Autowired
    @Qualifier("visibilityRoutes")
    lateinit var visibilityRoutes: RouterFunction<ServerResponse>

    @BeforeEach
    fun setup() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build()
    }

    companion object {
        @JvmStatic
        @TempDir
        lateinit var tempDir: Path

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val process = ProcessBuilder("openssl", "rand", "-base64", "48").start()
            val secret = process.inputStream.bufferedReader().use { it.readText().trim() }
            val exitCode = process.waitFor()
            check(exitCode == 0 && secret.isNotBlank()) { "openssl command failed" }
            registry.add("jwt.secret") { secret }
            registry.add("storage.file.local.root-dir") { tempDir.toAbsolutePath().toString() }
        }
    }

    @Test
    fun `embedded gateway configuration and visibility route integration`() {
        // Assert absence of standalone beans by type
        assertTrue(applicationContext.getBeanNamesForType(cc.midolog.gateway.proxy.ProxyHandler::class.java).isEmpty(), "ProxyHandler should not be present")
        assertTrue(applicationContext.getBeanNamesForType(cc.midolog.gateway.filter.JwtAuthFilter::class.java).isEmpty(), "JwtAuthFilter should not be present")

        // Assert presence of embedded beans by type
        assertTrue(applicationContext.getBeanNamesForType(cc.midolog.gateway.filter.AuthTokenRateLimitFilter::class.java).isNotEmpty(), "AuthTokenRateLimitFilter must be present")
        assertTrue(applicationContext.getBeanNamesForType(cc.midolog.gateway.visibility.RequestVisibilityFilter::class.java).isNotEmpty(), "RequestVisibilityFilter must be present")
        assertTrue(applicationContext.getBeanNamesForType(cc.midolog.gateway.visibility.RequestEventStore::class.java).isNotEmpty(), "RequestEventStore must be present")
        assertTrue(applicationContext.getBeanNamesForType(cc.midolog.gateway.visibility.RequestVisibilityHandler::class.java).isNotEmpty(), "RequestVisibilityHandler must be present")

        // Route predicate verification
        val apiExchange: ServerWebExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/sample/ping"))
        val apiRequest = MockServerRequest.builder().method(org.springframework.http.HttpMethod.GET).uri(java.net.URI("/api/sample/ping")).exchange(apiExchange).build()
        StepVerifier.create(visibilityRoutes.route(apiRequest))
            .verifyComplete() // empty Mono means no match
            
        val internalExchange: ServerWebExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/internal/gateway/requests"))
        val internalRequest = MockServerRequest.builder().method(org.springframework.http.HttpMethod.GET).uri(java.net.URI("/internal/gateway/requests")).exchange(internalExchange).build()
        StepVerifier.create(visibilityRoutes.route(internalRequest))
            .consumeNextWith { assertNotNull(it) }
            .verifyComplete()

        // Assert handler mapping order
        val routerMapping = applicationContext.getBean("routerFunctionMapping", RouterFunctionMapping::class.java)
        val requestMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping::class.java)
        assertEquals(-1, routerMapping.order, "RouterFunctionMapping order must be -1")
        assertEquals(0, requestMapping.order, "RequestMappingHandlerMapping order must be 0")

        val token = jwtProvider.issue("test-user")

        // 1. API ping with JWT
        webTestClient.get().uri("/api/sample/ping")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk

        // 2. internal gateway requests with JWT
        webTestClient.get().uri("/internal/gateway/requests")
            .header(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$[?(@.path == '/api/sample/ping')]").exists()

        // 3. internal gateway requests without JWT
        webTestClient.get().uri("/internal/gateway/requests")
            .exchange()
            .expectStatus().isUnauthorized
    }
}
