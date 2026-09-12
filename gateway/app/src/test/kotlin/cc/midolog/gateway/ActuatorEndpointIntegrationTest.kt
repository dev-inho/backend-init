package cc.midolog.gateway

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=this_is_a_test_secret_for_jwt_auth_filter",
        "gateway.routes.application-url=http://localhost:8081",
        "gateway.routes.batch-url=http://localhost:8082",
        "management.health.redis.enabled=false"
    ]
)
class ActuatorEndpointIntegrationTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `health and metrics endpoints are exposed by default, prometheus is not`() {
        val webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build()

        webTestClient.get().uri("/actuator/health")
            .exchange()
            .expectStatus().isOk

        webTestClient.get().uri("/actuator/metrics")
            .exchange()
            .expectStatus().isOk

        webTestClient.get().uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isEqualTo(org.springframework.http.HttpStatus.BAD_GATEWAY)
    }
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=this_is_a_test_secret_for_jwt_auth_filter",
        "gateway.routes.application-url=http://localhost:8081",
        "gateway.routes.batch-url=http://localhost:8082",
        "management.health.redis.enabled=false",
        "management.endpoints.web.exposure.include=health,metrics,prometheus"
    ]
)
class PrometheusEndpointIntegrationTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    fun `prometheus endpoint is exposed when explicitly included`() {
        val webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build()

        webTestClient.get().uri("/actuator/prometheus")
            .exchange()
            .expectStatus().isOk
    }
}
