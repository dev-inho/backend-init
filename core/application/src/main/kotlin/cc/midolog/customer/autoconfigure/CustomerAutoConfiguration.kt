package cc.midolog.customer.autoconfigure

import cc.midolog.customer.CustomerDescriptor
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

/**
 * 고객 모듈 탑재 및 활성화 상태를 검증하는 자동 구성 클래스.
 *
 * `app.customer` 프로퍼티와 등록된 [CustomerDescriptor] 빈의 일치 여부를
 * BeanFactoryPostProcessor를 통해 애플리케이션 기동 초기에 조기 검증(fail-fast)한다.
 */
@AutoConfiguration
class CustomerAutoConfiguration {

    companion object {
        @Bean
        @JvmStatic
        fun customerValidationBeanFactoryPostProcessor(): BeanFactoryPostProcessor {
            return BeanFactoryPostProcessor { beanFactory ->
                val environment = beanFactory.getBean(Environment::class.java)
                val customerProperty = environment.getProperty("app.customer")?.trim()?.takeIf { it.isNotEmpty() }
                val descriptorBeanNames = beanFactory.getBeanNamesForType(CustomerDescriptor::class.java)
                val descriptors = descriptorBeanNames.map { beanFactory.getBean(it, CustomerDescriptor::class.java) }

                if (customerProperty != null) {
                    check(descriptors.isNotEmpty()) {
                        "app.customer 프로퍼티가 '$customerProperty'로 설정되었으나 일치하는 CustomerDescriptor 빈이 존재하지 않습니다."
                    }
                    val descriptor = descriptors.singleOrNull()
                        ?: error("CustomerDescriptor 빈이 다수 등록되었습니다: ${descriptors.map { it.name }}")
                    check(descriptor.name == customerProperty) {
                        "app.customer 프로퍼티('$customerProperty')와 등록된 CustomerDescriptor('${descriptor.name}')가 일치하지 않습니다."
                    }
                } else {
                    check(descriptors.isEmpty()) {
                        "CustomerDescriptor('${descriptors.first().name}') 빈이 등록되었으나 app.customer 프로퍼티가 설정되지 않았습니다."
                    }
                }
            }
        }
    }
}
