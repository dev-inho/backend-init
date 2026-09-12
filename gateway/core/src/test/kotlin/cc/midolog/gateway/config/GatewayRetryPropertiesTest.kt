package cc.midolog.gateway.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import java.time.Duration

class GatewayRetryPropertiesTest {

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(TestConfig::class.java)

    @EnableConfigurationProperties(GatewayRetryProperties::class)
    class TestConfig

    @Test
    fun `default values are 1 and 100ms`() {
        contextRunner.run { context ->
            val properties = context.getBean(GatewayRetryProperties::class.java)
            assertEquals(1, properties.maxAttempts)
            assertEquals(Duration.ofMillis(100), properties.backoff)
        }
    }

    @Test
    fun `maxAttempts can be 1, 2, 3`() {
        contextRunner.withPropertyValues("gateway.proxy.retry.max-attempts=3")
            .run { context ->
                val properties = context.getBean(GatewayRetryProperties::class.java)
                assertEquals(3, properties.maxAttempts)
            }
    }

    @Test
    fun `maxAttempts 0 fails context startup`() {
        contextRunner.withPropertyValues("gateway.proxy.retry.max-attempts=0")
            .run { context ->
                assertThrows<IllegalStateException> { context.getBean(GatewayRetryProperties::class.java) }
            }
    }

    @Test
    fun `maxAttempts 4 fails context startup`() {
        contextRunner.withPropertyValues("gateway.proxy.retry.max-attempts=4")
            .run { context ->
                assertThrows<IllegalStateException> { context.getBean(GatewayRetryProperties::class.java) }
            }
    }

    @Test
    fun `negative backoff fails context startup`() {
        contextRunner.withPropertyValues("gateway.proxy.retry.backoff=-10ms")
            .run { context ->
                assertThrows<IllegalStateException> { context.getBean(GatewayRetryProperties::class.java) }
            }
    }
}
