package cc.midolog.storage.jpa.autoconfigure

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

/**
 * JPA 영속성 프로바이더 불일치 시 관련 Spring Boot 자동 구성을 제외하는 [EnvironmentPostProcessor].
 *
 * `storage.persistence.provider`가 'jpa'가 아닐 때 Hibernate JPA 및 Spring Data JPA 자동 구성을
 * `spring.autoconfigure.exclude`에 주입하여, 불필요한 EntityManagerFactory 및 JpaRepository 빈 생성을 방지한다.
 */
class JpaDefaultPropertiesEnvironmentPostProcessor : EnvironmentPostProcessor {

    override fun postProcessEnvironment(environment: ConfigurableEnvironment, application: SpringApplication) {
        val provider = environment.getProperty("storage.persistence.provider")
        if (provider != null && provider != "jpa") {
            val existing = environment.getProperty("spring.autoconfigure.exclude")
            val excludes = listOf(
                "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
                "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
            )
            val merged = if (existing.isNullOrBlank()) {
                excludes.joinToString(",")
            } else {
                "$existing," + excludes.joinToString(",")
            }
            environment.propertySources.addFirst(
                MapPropertySource("jpaExcludeProperties", mapOf("spring.autoconfigure.exclude" to merged))
            )
        }
    }
}
