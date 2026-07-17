package cc.midolog.persistence

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource

class RuntimeProfileConfigTest {

    @Test
    fun `base application config does not activate profiles implicitly`() {
        val baseConfig = resourceText("application.yml")

        assertFalse(baseConfig.contains("active:"), "base application.yml must not set spring.profiles.active")
    }

    @Test
    fun `local application config owns local datasource and redis defaults`() {
        val localConfig = resourceText("application-local.yml")

        assertTrue(localConfig.contains("jdbc:postgresql://localhost:5432/backend"))
        assertTrue(localConfig.contains("port: \${REDIS_PORT:6380}"))
    }

    private fun resourceText(path: String): String =
        ClassPathResource(path).inputStream.bufferedReader().use { it.readText() }
}
