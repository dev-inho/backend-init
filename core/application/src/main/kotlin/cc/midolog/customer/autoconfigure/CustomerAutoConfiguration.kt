package cc.midolog.customer.autoconfigure

import cc.midolog.customer.CustomerDescriptor
import cc.midolog.customer.CustomerDescriptorMetadata
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

/**
 * 고객 모듈 탑재 및 활성화 상태를 검증하는 자동 구성 클래스.
 *
 * `app.customer` 프로퍼티와 등록된 [CustomerDescriptor] 빈 정의 메타데이터의 일치 여부를
 * BeanFactoryPostProcessor를 통해 애플리케이션 기동 초기에 조기 검증(fail-fast)한다.
 * 빈 인스턴스를 조기 획득(getBean)하지 않고 빈 정의 메타데이터만을 검사하여 조기 인스턴스화 부수 효과를 방지한다.
 */
@AutoConfiguration
class CustomerAutoConfiguration {

    companion object {
        /**
         * 빈 팩토리 후처리기를 생성하여 환경 프로퍼티와 빈 메타데이터 일치 여부를 검증한다.
         */
        @Bean
        @JvmStatic
        fun customerValidationBeanFactoryPostProcessor(
            environment: Environment,
        ): BeanFactoryPostProcessor {
            return BeanFactoryPostProcessor { beanFactory ->
                val customerProperty = environment.getProperty("app.customer")?.trim()?.takeIf { it.isNotEmpty() }
                val descriptorBeanNames = beanFactory.getBeanNamesForType(
                    CustomerDescriptor::class.java,
                    true,
                    false,
                )

                val descriptorNames = descriptorBeanNames.map { beanName ->
                    val beanDefinition = beanFactory.getBeanDefinition(beanName)
                    extractCustomerName(beanDefinition) ?: beanName
                }

                if (customerProperty != null) {
                    check(descriptorNames.isNotEmpty()) {
                        "app.customer 프로퍼티가 '$customerProperty'로 설정되었으나 일치하는 CustomerDescriptor 빈이 존재하지 않습니다."
                    }
                    val descriptorName = descriptorNames.singleOrNull()
                        ?: error("CustomerDescriptor 빈이 다수 등록되었습니다: $descriptorNames")
                    check(descriptorName == customerProperty) {
                        "app.customer 프로퍼티('$customerProperty')와 등록된 CustomerDescriptor('$descriptorName')가 일치하지 않습니다."
                    }
                } else {
                    check(descriptorNames.isEmpty()) {
                        "CustomerDescriptor('${descriptorNames.first()}') 빈이 등록되었으나 app.customer 프로퍼티가 설정되지 않았습니다."
                    }
                }
            }
        }

        private fun extractCustomerName(beanDefinition: BeanDefinition): String? {
            if (beanDefinition is AnnotatedBeanDefinition) {
                val metadata = beanDefinition.factoryMethodMetadata
                val customAttrs = metadata?.getAnnotationAttributes(CustomerDescriptorMetadata::class.java.name)
                val customName = customAttrs?.get("name") as? String
                if (!customName.isNullOrBlank()) return customName

                val qualifierAttrs = metadata?.getAnnotationAttributes("org.springframework.beans.factory.annotation.Qualifier")
                val qualifierValue = qualifierAttrs?.get("value") as? String
                if (!qualifierValue.isNullOrBlank()) return qualifierValue
            }

            val attr = (beanDefinition.getAttribute("customerName")
                ?: beanDefinition.getAttribute("customer")
                ?: beanDefinition.getAttribute("name")) as? String
            if (!attr.isNullOrBlank()) return attr

            val constrVal = beanDefinition.constructorArgumentValues
                .getIndexedArgumentValue(0, String::class.java)?.value as? String
            if (!constrVal.isNullOrBlank()) return constrVal

            val genericVal = beanDefinition.constructorArgumentValues
                .getGenericArgumentValue(String::class.java)?.value as? String
            if (!genericVal.isNullOrBlank()) return genericVal

            return null
        }
    }
}
