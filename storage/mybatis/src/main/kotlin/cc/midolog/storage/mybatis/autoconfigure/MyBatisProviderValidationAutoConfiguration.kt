package cc.midolog.storage.mybatis.autoconfigure

import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

/**
 * MyBatis 저장소 모듈을 단독으로 사용할 때도 검증이 수행되도록 독자적으로 구성된 검증 자동 구성 클래스입니다.
 *
 * JPA 모듈과 중복될 수 있으나, 공통 코어 모듈이 없으므로 각각의 Starter가 독립적인 classpath 안전성을
 * 보장하기 위해 각 모듈 내에 검증 클래스를 유지합니다.
 * 두 모듈이 동시에 등록되더라도 검증 로직은 멱등적(idempotent)이며 동일한 provider 설정 하에서 두 검증 모두 통과합니다.
 *
 * 일반 싱글톤 빈(특히 도메인 포트를 생성자 주입받는 소비자 빈)이 인스턴스화되기 전에 provider 설정을 조기 검증(fail-fast)하기 위해
 * [BeanFactoryPostProcessor]를 companion object의 @JvmStatic @Bean으로 등록합니다.
 * 이를 통해 Spring이 자동 구성 클래스 인스턴스를 조기 인스턴스화하지 않고 정적 바이트코드 메서드로 post-processor를 등록합니다.
 */
@AutoConfiguration
class MyBatisProviderValidationAutoConfiguration {

    companion object {
        @Bean
        @JvmStatic
        fun myBatisProviderValidationBeanFactoryPostProcessor(): BeanFactoryPostProcessor {
            return BeanFactoryPostProcessor { beanFactory ->
                val environment = beanFactory.getBean(Environment::class.java)
                val provider = environment.getProperty("storage.persistence.provider")
                check(!provider.isNullOrBlank()) {
                    "storage.persistence.provider 값이 설정되지 않았습니다. jpa 또는 mybatis를 지정하세요."
                }
                check(provider == "jpa" || provider == "mybatis") {
                    "알 수 없는 persistence provider입니다: $provider. 지원되는 값: jpa, mybatis"
                }
            }
        }
    }
}
