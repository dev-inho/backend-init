package cc.midolog.customers.acme

import cc.midolog.ApplicationServer
import cc.midolog.business.service.SampleService
import cc.midolog.customer.CustomerDescriptor
import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.cache.SampleCachePort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse

@SpringBootTest(classes = [ApplicationServer::class, AcmeApplicationIntegrationTest.TestConfig::class])
@TestPropertySource(properties = [
    "app.customer=acme",
    "gateway.mode=embedded",
    "storage.persistence.provider=mybatis",
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.sql.init.mode=never",
    "spring.data.redis.repositories.enabled=false",
    "storage.file.provider=local",
    "storage.file.local.root-dir=/tmp/acme-test-storage",
    "storage.file.max-size-bytes=8192",
    "storage.file.allowed-content-types=image/png,image/jpeg,application/pdf",
    "jwt.secret=this-is-a-dummy-jwt-secret-for-testing-only-must-be-long"
])
class AcmeApplicationIntegrationTest {

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun testSampleRepositoryPort(): SampleRepositoryPort = object : SampleRepositoryPort {
            override suspend fun save(sample: Sample): Sample = sample
            override suspend fun findById(id: String): Sample? = null
        }
    }

    @Autowired
    private lateinit var customerDescriptor: CustomerDescriptor

    @Autowired
    private lateinit var sampleService: SampleService

    @Autowired
    private lateinit var sampleCachePort: SampleCachePort

    @Autowired
    @Qualifier("acmeRouter")
    private lateinit var acmeRouter: RouterFunction<ServerResponse>

    @Test
    fun `Acme 고객 활성화 시 Descriptor 및 SampleSavePolicy 접두어가 정상 적용되어야 한다`(): Unit = runBlocking {
        assertThat(customerDescriptor.name).isEqualTo("acme")

        val saved = sampleService.save(Sample("acme-test-1", "original-name"))
        assertThat(saved.name).isEqualTo("[acme] original-name")

        // no-op 캐시 포트이므로 캐시 조회 시 null 반환
        val cached = sampleCachePort.get("acme-test-1")
        assertThat(cached).isNull()
    }

    @Test
    fun `GET api acme info 엔드포인트가 정상 응답해야 한다`() {
        val client = WebTestClient.bindToRouterFunction(acmeRouter).build()
        client.get()
            .uri("/api/acme/info")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.customer").isEqualTo("acme")
            .jsonPath("$.status").isEqualTo("active")
    }
}
