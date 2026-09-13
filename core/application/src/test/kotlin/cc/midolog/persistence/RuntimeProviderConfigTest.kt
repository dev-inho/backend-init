package cc.midolog.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.core.io.ClassPathResource

class RuntimeProviderConfigTest {

    @Test
    fun `base application config does not activate profiles implicitly`() {
        val baseConfig = resourceText("application.yml")

        assertFalse(baseConfig.contains("spring.profiles.active:"), "base application.yml must not set spring.profiles.active directly")
    }

    @Test
    fun `local application config owns local datasource and redis defaults`() {
        val localConfig = resourceText("application-local.yml")

        assertTrue(localConfig.contains("jdbc:postgresql://localhost:5432/backend"))
        assertTrue(localConfig.contains("port: \${REDIS_PORT:6380}"))
    }

    @Test
    fun `flyway migration owns storage schema`() {
        val migration = resourceText("db/migration/V1__create_storage_tables.sql")

        listOf(
            "CREATE TABLE IF NOT EXISTS sample",
            "CREATE TABLE IF NOT EXISTS app_user",
            "CREATE TABLE IF NOT EXISTS scalar_sample",
            "CREATE TABLE IF NOT EXISTS relation_parent",
            "CREATE TABLE IF NOT EXISTS relation_child",
            "CONSTRAINT fk_relation_child_parent",
            "CREATE INDEX IF NOT EXISTS idx_relation_child_parent_id",
        ).forEach { expected ->
            assertTrue(migration.contains(expected), "migration must contain: $expected")
        }
    }

    @Test
    fun `base application config sets gateway mode to embedded`() {
        val baseConfig = resourceText("application.yml")

        assertTrue(baseConfig.contains("gateway:\n  mode: embedded"), "base application.yml must set gateway.mode: embedded")
    }

    @Test
    fun `base application config sets default persistence provider to mybatis`() {
        val baseConfig = resourceText("application.yml")

        assertTrue(baseConfig.contains("storage:\n  persistence:\n    provider: \${STORAGE_PERSISTENCE_PROVIDER:mybatis}"), "base application.yml must set default persistence provider")
    }

    @Test
    fun `base application config does not contain legacy jpa or mybatis config files`() {
        val mybatisResource = ClassPathResource("application-mybatis.yml")
        assertFalse(mybatisResource.exists(), "application-mybatis.yml must be removed")

        val jpaResource = ClassPathResource("application-jpa.yml")
        assertFalse(jpaResource.exists(), "application-jpa.yml must be removed")
    }

    @Test
    fun `default provider is mybatis when context loads`() {
        SpringApplicationBuilder(TestConfig::class.java)
            .web(WebApplicationType.NONE)
            .run().use { context ->
                assertEquals("mybatis", context.environment.getProperty("storage.persistence.provider"))
            }
    }

    @Test
    fun `provider property can be overridden by environment`() {
        SpringApplicationBuilder(TestConfig::class.java)
            .web(WebApplicationType.NONE)
            .properties("STORAGE_PERSISTENCE_PROVIDER=jpa")
            .run().use { context ->
                assertEquals("jpa", context.environment.getProperty("storage.persistence.provider"))
            }
    }

    @org.springframework.context.annotation.Configuration
    class TestConfig

    private fun resourceText(path: String): String =
        ClassPathResource(path).inputStream.bufferedReader().use { it.readText() }
}
