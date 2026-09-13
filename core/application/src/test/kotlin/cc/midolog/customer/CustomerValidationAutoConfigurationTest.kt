package cc.midolog.customer

import cc.midolog.customer.autoconfigure.CustomerAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class CustomerValidationAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(CustomerAutoConfiguration::class.java))

    @Configuration
    class AcmeDescriptorConfig {
        @Bean
        fun customerDescriptor(): CustomerDescriptor = CustomerDescriptor(name = "acme")
    }

    @Configuration
    class BetaDescriptorConfig {
        @Bean
        fun customerDescriptor(): CustomerDescriptor = CustomerDescriptor(name = "beta")
    }

    interface MissingPort

    class ConsumerBean(val missingPort: MissingPort)

    @Test
    fun `app_customer가 acme로 설정되었으나 descriptor 빈이 없으면 기동 실패하고 메시지에 app_customer와 acme가 포함되어야 한다`() {
        contextRunner
            .withPropertyValues("app.customer=acme")
            .run { context ->
                assertThat(context).hasFailed()
                val cause = generateSequence(context.startupFailure) { it.cause }.last()
                assertThat(cause.message)
                    .contains("app.customer")
                    .contains("acme")
            }
    }

    @Test
    fun `descriptor acme 빈이 등록되었으나 app_customer 프로퍼티가 없으면 기동 실패해야 한다`() {
        contextRunner
            .withUserConfiguration(AcmeDescriptorConfig::class.java)
            .run { context ->
                assertThat(context).hasFailed()
                val cause = generateSequence(context.startupFailure) { it.cause }.last()
                assertThat(cause.message)
                    .contains("app.customer")
                    .contains("acme")
            }
    }

    @Test
    fun `app_customer 프로퍼티와 descriptor 빈 이름이 불일치하면 기동 실패해야 한다`() {
        contextRunner
            .withPropertyValues("app.customer=acme")
            .withUserConfiguration(BetaDescriptorConfig::class.java)
            .run { context ->
                assertThat(context).hasFailed()
                val cause = generateSequence(context.startupFailure) { it.cause }.last()
                assertThat(cause.message)
                    .contains("acme")
                    .contains("beta")
            }
    }

    @Test
    fun `app_customer 프로퍼티와 descriptor 빈이 둘 다 없으면 정상 기동되어야 한다`() {
        contextRunner
            .run { context ->
                assertThat(context).hasNotFailed()
            }
    }

    @Test
    fun `app_customer 프로퍼티와 descriptor 빈이 모두 acme로 일치하면 정상 기동되어야 한다`() {
        contextRunner
            .withPropertyValues("app.customer=acme")
            .withUserConfiguration(AcmeDescriptorConfig::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
            }
    }

    @Test
    fun `BFPP 검증은 누락된 빈 의존성을 요구하는 소비자 빈이 있더라도 NoSuchBeanDefinitionException 전에 fail-fast 예외가 발생해야 한다`() {
        contextRunner
            .withPropertyValues("app.customer=acme")
            .withUserConfiguration(ConsumerBean::class.java)
            .run { context ->
                assertThat(context).hasFailed()
                val causes = generateSequence(context.startupFailure) { it.cause }.toList()
                assertThat(causes)
                    .`as`("NoSuchBeanDefinitionException 전에 CustomerValidation 예외가 먼저 발생해야 한다")
                    .noneMatch { it is org.springframework.beans.factory.NoSuchBeanDefinitionException }
                assertThat(causes.any { it.message?.contains("app.customer") == true && it.message?.contains("acme") == true })
                    .isTrue()
            }
    }
}
