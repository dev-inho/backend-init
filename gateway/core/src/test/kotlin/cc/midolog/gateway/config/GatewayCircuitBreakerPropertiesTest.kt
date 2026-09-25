package cc.midolog.gateway.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration

class GatewayCircuitBreakerPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfig::class.java)

    @EnableConfigurationProperties(GatewayCircuitBreakerProperties::class)
    class TestConfig

    @Test
    fun `default values are configured properly`() {
        contextRunner.run { context ->
            val properties = context.getBean(GatewayCircuitBreakerProperties::class.java)
            assertTrue(properties.enabled)
            assertEquals(3, properties.failureThreshold)
            assertEquals(Duration.ofSeconds(5), properties.openDuration)
            assertEquals(1, properties.halfOpenPermits)
            assertEquals(50, properties.maxConcurrentCalls)
        }
    }

    @Test
    fun `custom valid values bind properly`() {
        contextRunner.withPropertyValues(
            "gateway.proxy.circuit-breaker.enabled=false",
            "gateway.proxy.circuit-breaker.failure-threshold=5",
            "gateway.proxy.circuit-breaker.open-duration=10s",
            "gateway.proxy.circuit-breaker.half-open-permits=2",
            "gateway.proxy.circuit-breaker.max-concurrent-calls=100"
        ).run { context ->
            val properties = context.getBean(GatewayCircuitBreakerProperties::class.java)
            assertEquals(false, properties.enabled)
            assertEquals(5, properties.failureThreshold)
            assertEquals(Duration.ofSeconds(10), properties.openDuration)
            assertEquals(2, properties.halfOpenPermits)
            assertEquals(100, properties.maxConcurrentCalls)
        }
    }

    @Test
    fun `failureThreshold less than 1 fails context startup`() {
        contextRunner.withPropertyValues("gateway.proxy.circuit-breaker.failure-threshold=0")
            .run { context ->
                assertThrows<IllegalStateException> { context.getBean(GatewayCircuitBreakerProperties::class.java) }
            }
    }

    @Test
    fun `zero or negative openDuration fails context startup`() {
        contextRunner.withPropertyValues("gateway.proxy.circuit-breaker.open-duration=0s")
            .run { context ->
                assertThrows<IllegalStateException> { context.getBean(GatewayCircuitBreakerProperties::class.java) }
            }

        contextRunner.withPropertyValues("gateway.proxy.circuit-breaker.open-duration=-1s")
            .run { context ->
                assertThrows<IllegalStateException> { context.getBean(GatewayCircuitBreakerProperties::class.java) }
            }
    }

    @Test
    fun `halfOpenPermits less than 1 fails context startup`() {
        contextRunner.withPropertyValues("gateway.proxy.circuit-breaker.half-open-permits=0")
            .run { context ->
                assertThrows<IllegalStateException> { context.getBean(GatewayCircuitBreakerProperties::class.java) }
            }
    }

    @Test
    fun `maxConcurrentCalls less than 1 fails context startup`() {
        contextRunner.withPropertyValues("gateway.proxy.circuit-breaker.max-concurrent-calls=0")
            .run { context ->
                assertThrows<IllegalStateException> { context.getBean(GatewayCircuitBreakerProperties::class.java) }
            }
    }
}
