package cc.midolog.gateway.config

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GatewayRoutePropertiesTest {

    @Configuration
    @EnableConfigurationProperties(GatewayRouteProperties::class)
    class Config

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(Config::class.java)

    @Test
    fun `기본값이 정상적으로 바인딩된다`() {
        contextRunner
            .withPropertyValues(
                "gateway.routes.application-url=http://app.local",
                "gateway.routes.batch-url=http://batch.local"
            )
            .run { context ->
                val properties = context.getBean(GatewayRouteProperties::class.java)
                val healthCheck = properties.healthCheck
                assertEquals(false, healthCheck.enabled)
                assertEquals("/actuator/health", healthCheck.path)
                assertEquals(Duration.ofSeconds(10), healthCheck.interval)
                assertEquals(3, healthCheck.unhealthyThreshold)
                assertEquals(1, healthCheck.healthyThreshold)
            }
    }

    @Test
    fun `interval이 0이거나 음수이면 기동 실패한다`() {
        contextRunner
            .withPropertyValues(
                "gateway.routes.application-url=http://app.local",
                "gateway.routes.batch-url=http://batch.local",
                "gateway.routes.health-check.interval=0s"
            )
            .run { context ->
                assertTrue(context.startupFailure != null)
                assertTrue(context.startupFailure!!.message!!.contains("interval must be greater than 0"))
            }

        contextRunner
            .withPropertyValues(
                "gateway.routes.application-url=http://app.local",
                "gateway.routes.batch-url=http://batch.local",
                "gateway.routes.health-check.interval=-5s"
            )
            .run { context ->
                assertTrue(context.startupFailure != null)
                assertTrue(context.startupFailure!!.message!!.contains("interval must be greater than 0"))
            }
    }

    @Test
    fun `threshold가 0이하이면 기동 실패한다`() {
        contextRunner
            .withPropertyValues(
                "gateway.routes.application-url=http://app.local",
                "gateway.routes.batch-url=http://batch.local",
                "gateway.routes.health-check.unhealthy-threshold=0"
            )
            .run { context ->
                assertTrue(context.startupFailure != null)
                assertTrue(context.startupFailure!!.message!!.contains("unhealthy-threshold must be greater than 0"))
            }

        contextRunner
            .withPropertyValues(
                "gateway.routes.application-url=http://app.local",
                "gateway.routes.batch-url=http://batch.local",
                "gateway.routes.health-check.healthy-threshold=0"
            )
            .run { context ->
                assertTrue(context.startupFailure != null)
                assertTrue(context.startupFailure!!.message!!.contains("healthy-threshold must be greater than 0"))
            }
    }

    @Test
    fun `path가 slash로 시작하지 않으면 기동 실패한다`() {
        contextRunner
            .withPropertyValues(
                "gateway.routes.application-url=http://app.local",
                "gateway.routes.batch-url=http://batch.local",
                "gateway.routes.health-check.path=health"
            )
            .run { context ->
                assertTrue(context.startupFailure != null)
                assertTrue(context.startupFailure!!.message!!.contains("path must start with '/'"))
            }
    }
}
