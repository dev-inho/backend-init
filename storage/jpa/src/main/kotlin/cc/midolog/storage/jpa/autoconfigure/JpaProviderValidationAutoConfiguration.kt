package cc.midolog.storage.jpa.autoconfigure

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.env.Environment

@AutoConfiguration
class JpaProviderValidationAutoConfiguration(private val environment: Environment) {

    @PostConstruct
    fun validateProvider() {
        val provider = environment.getProperty("storage.persistence.provider")
        if (provider != "jpa" && provider != "mybatis") {
            throw IllegalStateException("storage.persistence.provider 값이 유효하지 않거나 누락되었습니다. 현재 값: $provider, 허용값: jpa, mybatis")
        }
    }
}
