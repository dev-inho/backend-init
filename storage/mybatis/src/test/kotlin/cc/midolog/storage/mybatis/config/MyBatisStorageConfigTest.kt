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
    fun `environment post processor is loaded by spring factories loader and applies default mybatis properties`() {
        val factory = org.springframework.boot.support.EnvironmentPostProcessorsFactory.fromSpringFactories(javaClass.classLoader)
        val postProcessors = factory.getEnvironmentPostProcessors(
            org.springframework.boot.logging.DeferredLogs(),
            org.springframework.boot.bootstrap.DefaultBootstrapContext()
        )

        val myBatisProcessor = postProcessors.filterIsInstance<cc.midolog.storage.mybatis.autoconfigure.MyBatisDefaultPropertiesEnvironmentPostProcessor>()
            .firstOrNull()
        org.junit.jupiter.api.Assertions.assertNotNull(
            myBatisProcessor,
            "MyBatisDefaultPropertiesEnvironmentPostProcessor must be discovered by SpringFactoriesLoader via META-INF/spring.factories"
        )

        val environment = org.springframework.mock.env.MockEnvironment()
        myBatisProcessor!!.postProcessEnvironment(environment, org.springframework.boot.SpringApplication())

        org.junit.jupiter.api.Assertions.assertEquals(
            "classpath*:mapper/**/*.xml",
            environment.getProperty("mybatis.mapper-locations")
        )
        org.junit.jupiter.api.Assertions.assertEquals(
            "true",
            environment.getProperty("mybatis.configuration.map-underscore-to-camel-case")
        )
    }
}
