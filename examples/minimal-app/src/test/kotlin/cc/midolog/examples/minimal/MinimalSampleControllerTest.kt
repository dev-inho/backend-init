package cc.midolog.examples.minimal

import cc.midolog.examples.minimal.controller.CreateSampleRequest
import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * 도메인 포트를 통해 노출된 최소 소비자 컨트롤러(/samples)의 HTTP 엔드포인트 통합 테스트.
 *
 * 외부 영속성 라이브러리 import 없이 웹 계층에서 save 및 findById HTTP 호출이
 * 도메인 포트 연동을 통해 정상 수행되는지 검증한다.
 */
@SpringBootTest(
    classes = [MinimalApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "storage.persistence.provider=jpa",
        "spring.datasource.url=jdbc:h2:mem:minimal_web;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
    ]
)
class MinimalSampleControllerTest {

    @LocalServerPort
    private var port: Int = 0

    @Autowired
    private lateinit var sampleRepositoryPort: SampleRepositoryPort

    private val client by lazy {
        WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()
    }

    @Test
    fun `controller save and find flow succeeds via http endpoint`() = runTest {
        val request = CreateSampleRequest(id = "web-1", name = "Web Sample")

        client.post()
            .uri("/samples")
            .bodyValue(request)
            .exchange()
            .expectStatus().isCreated
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.data.id").isEqualTo("web-1")
            .jsonPath("$.data.name").isEqualTo("Web Sample")

        val found = sampleRepositoryPort.findById("web-1")
        assertNotNull(found)
        assertEquals("Web Sample", found?.name)

        client.get()
            .uri("/samples/web-1")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.success").isEqualTo(true)
            .jsonPath("$.data.id").isEqualTo("web-1")
            .jsonPath("$.data.name").isEqualTo("Web Sample")
    }
}
