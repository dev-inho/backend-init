package cc.midolog.storage.mybatis.autoconfigure

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class MyBatisDefaultPropertiesEnvironmentPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val defaultProperties = mutableMapOf<String, Any>(
            "mybatis.mapper-locations" to "classpath*:mapper/**/*.xml",
            "mybatis.configuration.map-underscore-to-camel-case" to "true"
        )
        environment.propertySources.addLast(MapPropertySource("mybatisDefaultProperties", defaultProperties))

        val provider = environment.getProperty("storage.persistence.provider")
        if (provider != null && provider != "mybatis") {
            val existing = environment.getProperty("spring.autoconfigure.exclude")
            val excludes = listOf(
                "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration",
                "org.mybatis.spring.boot.autoconfigure.MybatisLanguageDriverAutoConfiguration"
            )
            val merged = if (existing.isNullOrBlank()) {
                excludes.joinToString(",")
            } else {
                "$existing," + excludes.joinToString(",")
            }
            environment.propertySources.addFirst(
                MapPropertySource("mybatisExcludeProperties", mapOf("spring.autoconfigure.exclude" to merged))
            )
        }
    }
}
