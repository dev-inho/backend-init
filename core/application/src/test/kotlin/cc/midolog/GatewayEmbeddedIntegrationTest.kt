package cc.midolog

import cc.midolog.common.security.JwtProvider
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.sample.port.cache.SampleCachePort
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.HttpHeaders
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.server.support.RouterFunctionMapping
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping
import java.io.BufferedReader
import java.io.InputStreamReader

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
        "storage.file.local.root-dir=/tmp/storage",
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

    @BeforeEach
    fun setup() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val process = ProcessBuilder("openssl", "rand", "-base64", "48").start()
            val secret = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText().trim() }
            process.waitFor()
            registry.add("jwt.secret") { secret }
        }
    }

    @Test
    fun `embedded gateway configuration and visibility route integration`() {
        // Assert absence of standalone beans
        assertFalse(applicationContext.containsBean("proxyHandler"), "ProxyHandler should not be present")
        assertFalse(applicationContext.containsBean("routes"), "routes RouterFunction should not be present")
        assertFalse(applicationContext.containsBean("jwtAuthFilter"), "JwtAuthFilter should not be present")

        // Assert presence of embedded beans
        assertTrue(applicationContext.containsBean("authTokenRateLimitFilter"), "AuthTokenRateLimitFilter must be present")
        assertTrue(applicationContext.containsBean("requestVisibilityFilter"), "RequestVisibilityFilter must be present")
        assertTrue(applicationContext.containsBean("requestEventStore"), "RequestEventStore must be present")
        assertTrue(applicationContext.containsBean("requestVisibilityHandler"), "RequestVisibilityHandler must be present")
        assertTrue(applicationContext.containsBean("visibilityRoutes"), "visibilityRoutes must be present")

        // Assert handler mapping order
        val routerMapping = applicationContext.getBean("routerFunctionMapping", RouterFunctionMapping::class.java)
        val requestMapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping::class.java)
        assertTrue(routerMapping.order == -1, "RouterFunctionMapping order must be -1")
        assertTrue(requestMapping.order == 0, "RequestMappingHandlerMapping order must be 0")

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
