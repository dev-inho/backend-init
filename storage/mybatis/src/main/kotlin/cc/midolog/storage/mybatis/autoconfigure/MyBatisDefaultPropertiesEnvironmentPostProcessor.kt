package cc.midolog.storage.mybatis.autoconfigure

import org.springframework.boot.SpringApplication
import org.springframework.boot.env.EnvironmentPostProcessor
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class MyBatisDefaultPropertiesEnvironmentPostProcessor : EnvironmentPostProcessor {
    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val url = environment.getProperty("spring.datasource.url", "")
        val driver = environment.getProperty("spring.datasource.driver-class-name", "")
        
        val isH2 = url.contains("h2") || driver.contains("h2")
        val hasH2Mappers = this::class.java.getResource("/mapper-h2") != null
        
        val mapperLocations = if (isH2 && hasH2Mappers) {
            "classpath*:mapper-h2/**/*.xml"
        } else {
            "classpath*:mapper/**/*.xml"
        }
        
        val properties = mapOf(
            "mybatis.mapper-locations" to mapperLocations,
            "mybatis.configuration.map-underscore-to-camel-case" to "true"
        )
        environment.propertySources.addLast(MapPropertySource("myBatisDefaultProperties", properties))
    }
}
