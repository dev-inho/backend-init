package cc.midolog.storage.mybatis.autoconfigure

import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.core.env.Environment

/**
 * MyBatis 저장소 모듈을 단독으로 사용할 때도 검증이 수행되도록 독자적으로 구성된 검증 클래스입니다.
 * (JPA 모듈과 중복될 수 있으나, 공통 코어 모듈이 없으므로 각각의 Starter가 독립적으로 안전을 보장하기 위해 유지합니다.)
 */
@AutoConfiguration
class MyBatisProviderValidationAutoConfiguration(
    private val environment: Environment
) {
    @PostConstruct
    fun validate() {
        val provider = environment.getProperty("storage.persistence.provider")
        check(!provider.isNullOrBlank()) {
            "storage.persistence.provider 값이 설정되지 않았습니다. jpa 또는 mybatis를 지정하세요."
        }
        check(provider == "jpa" || provider == "mybatis") {
            "알 수 없는 persistence provider입니다: $provider. 지원되는 값: jpa, mybatis"
        }
    }
}
