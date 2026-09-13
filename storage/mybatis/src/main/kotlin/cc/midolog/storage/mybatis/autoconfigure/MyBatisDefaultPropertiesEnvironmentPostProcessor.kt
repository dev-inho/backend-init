package cc.midolog.storage.mybatis.autoconfigure

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class MyBatisDefaultPropertiesEnvironmentPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val defaultProperties = mutableMapOf<String, Any>(
            "mybatis.mapper-locations" to "classpath*:mapper/**/*.xml",
            "mybatis.configuration.map-underscore-to-camel-case" to "true"
        )
        environment.propertySources.addLast(MapPropertySource("mybatisDefaultProperties", defaultProperties))
    }
}
