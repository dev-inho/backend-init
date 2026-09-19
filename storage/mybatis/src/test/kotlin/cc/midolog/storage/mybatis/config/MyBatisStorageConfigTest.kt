package cc.midolog.storage.mybatis.config

import cc.midolog.storage.mybatis.autoconfigure.MyBatisStorageAutoConfiguration
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class MyBatisStorageConfigTest {

    @Test
    fun `auto configuration loads`() {
        val config = MyBatisStorageAutoConfiguration()
        assertNotNull(config)
    }

    @Test
    fun `environment post processor is declared in boot imports`() {
        val resource = org.springframework.core.io.ClassPathResource("META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports")
        org.junit.jupiter.api.Assertions.assertTrue(resource.exists(), "EnvironmentPostProcessor.imports must exist")
        val content = resource.inputStream.bufferedReader().use { it.readText() }
        org.junit.jupiter.api.Assertions.assertTrue(
            content.contains("cc.midolog.storage.mybatis.autoconfigure.MyBatisDefaultPropertiesEnvironmentPostProcessor"),
            "MyBatisDefaultPropertiesEnvironmentPostProcessor must be registered"
        )
    }
}
