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

    private fun resourceText(path: String): String =
        ClassPathResource(path).inputStream.bufferedReader().use { it.readText() }
}
