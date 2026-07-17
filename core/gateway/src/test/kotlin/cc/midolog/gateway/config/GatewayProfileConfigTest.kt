package cc.midolog.gateway.config

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class GatewayProfileConfigTest {

    @Test
    fun `base gateway config does not activate profiles implicitly`() {
        val baseConfig = resourceText("application.yml")

        assertFalse(baseConfig.contains("active:"), "base gateway application.yml must not set spring.profiles.active")
    }

    @Test
    fun `local gateway config owns local redis and route defaults`() {
        val localConfig = resourceText("application-local.yml")

        assertTrue(localConfig.contains("port: \${REDIS_PORT:6380}"))
        assertTrue(localConfig.contains("http://localhost:8081"))
        assertTrue(localConfig.contains("http://localhost:8082"))
        assertTrue(localConfig.contains("application-urls: \${GATEWAY_APPLICATION_URLS:}"))
        assertTrue(localConfig.contains("enabled: \${GATEWAY_REQUEST_VISIBILITY_ENABLED:false}"))
        assertTrue(localConfig.contains("capacity: \${GATEWAY_REQUEST_VISIBILITY_CAPACITY:200}"))
    }

    @Test
    fun `base gateway config keeps request visibility disabled by default`() {
        val baseConfig = resourceText("application.yml")

        assertTrue(baseConfig.contains("application-urls: \${GATEWAY_APPLICATION_URLS:}"))
        assertTrue(baseConfig.contains("request-visibility:"))
        assertTrue(baseConfig.contains("enabled: false"))
        assertTrue(baseConfig.contains("capacity: 200"))
    }

    private fun resourceText(path: String): String =
        ClassPathResource(path).inputStream.bufferedReader().use { it.readText() }
}
