package cc.midolog.persistence

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.WebApplicationType
import org.springframework.boot.builder.SpringApplicationBuilder
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
    fun `base application config does not own storage persistence properties`() {
        val baseConfig = resourceText("application.yml")

        assertFalse(baseConfig.contains("mybatis:"), "base application.yml must not contain mybatis configuration")
        assertFalse(baseConfig.contains("mapper-locations"), "base application.yml must not contain mapper-locations")
        assertFalse(baseConfig.contains("spring.data.jpa") || baseConfig.contains("jpa:"), "base application.yml must not contain jpa configuration")
        assertFalse(baseConfig.contains("on-profile: jpa"), "base application.yml must not contain on-profile: jpa document")
    }

    @Test
    fun `storage profile resources own persistence properties`() {
        val mybatisResource = ClassPathResource("application-mybatis.yml")
        assertTrue(mybatisResource.exists(), "application-mybatis.yml must exist on classpath")
        val mybatisConfig = resourceText("application-mybatis.yml")
        assertTrue(mybatisConfig.contains("mapper-locations: classpath:mapper/**/*.xml"), "application-mybatis.yml must define mapper-locations")
        assertTrue(mybatisConfig.contains("map-underscore-to-camel-case: true"), "application-mybatis.yml must define map-underscore-to-camel-case")

        val jpaResource = ClassPathResource("application-jpa.yml")
        assertTrue(jpaResource.exists(), "application-jpa.yml must exist on classpath")
        val jpaConfig = resourceText("application-jpa.yml")
        assertTrue(jpaConfig.contains("jpa:"), "application-jpa.yml must contain jpa configuration")
        assertTrue(jpaConfig.contains("repositories:"), "application-jpa.yml must contain repositories configuration")
        assertTrue(jpaConfig.contains("enabled: true"), "application-jpa.yml must enable jpa repositories")
    }

    @Test
    fun `mybatis profile loads persistence properties from storage profile resource`() {
        SpringApplicationBuilder(TestConfig::class.java)
            .web(WebApplicationType.NONE)
            .profiles("mybatis")
            .run().use { context ->
                assertEquals("classpath:mapper/**/*.xml", context.environment.getProperty("mybatis.mapper-locations"))
                assertEquals("true", context.environment.getProperty("mybatis.configuration.map-underscore-to-camel-case"))
            }
    }

    @Test
    fun `jpa profile loads persistence properties from storage profile resource`() {
        SpringApplicationBuilder(TestConfig::class.java)
            .web(WebApplicationType.NONE)
            .profiles("jpa")
            .run().use { context ->
                assertEquals("true", context.environment.getProperty("spring.data.jpa.repositories.enabled"))
            }
    }

    @Test
    fun `non persistence profile does not load storage properties`() {
        SpringApplicationBuilder(TestConfig::class.java)
            .web(WebApplicationType.NONE)
            .profiles("test")
            .run().use { context ->
                assertNull(context.environment.getProperty("mybatis.mapper-locations"))
                assertNull(context.environment.getProperty("spring.data.jpa.repositories.enabled"))
            }
    }

    @org.springframework.context.annotation.Configuration
    class TestConfig

    private fun resourceText(path: String): String =
        ClassPathResource(path).inputStream.bufferedReader().use { it.readText() }
}

