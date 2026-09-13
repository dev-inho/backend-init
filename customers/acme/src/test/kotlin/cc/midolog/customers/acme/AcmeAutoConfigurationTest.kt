package cc.midolog.customers.acme

import cc.midolog.customer.CustomerDescriptor
import cc.midolog.sample.policy.SampleSavePolicy
import cc.midolog.sample.port.cache.SampleCachePort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.web.reactive.function.server.RouterFunction

class AcmeAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AcmeAutoConfiguration::class.java))

    @Test
    fun `app_customer가 acme일 때 모든 acme 빈이 등록되어야 한다`() {
        contextRunner
            .withPropertyValues("app.customer=acme")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(CustomerDescriptor::class.java)
                assertThat(context.getBean(CustomerDescriptor::class.java).name).isEqualTo("acme")

                assertThat(context).hasSingleBean(SampleSavePolicy::class.java)
                assertThat(context.getBean(SampleSavePolicy::class.java).order).isEqualTo(10)

                assertThat(context).hasSingleBean(SampleCachePort::class.java)
                assertThat(context).hasSingleBean(RouterFunction::class.java)
            }
    }

    @Test
    fun `app_customer 프로퍼티가 없으면 acme 빈이 등록되지 않아야 한다`() {
        contextRunner
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(CustomerDescriptor::class.java)
                assertThat(context).doesNotHaveBean(SampleSavePolicy::class.java)
                assertThat(context).doesNotHaveBean(SampleCachePort::class.java)
                assertThat(context).doesNotHaveBean(RouterFunction::class.java)
            }
    }

    @Test
    fun `app_customer가 beta일 때 acme 빈이 등록되지 않아야 한다`() {
        contextRunner
            .withPropertyValues("app.customer=beta")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).doesNotHaveBean(CustomerDescriptor::class.java)
                assertThat(context).doesNotHaveBean(SampleSavePolicy::class.java)
                assertThat(context).doesNotHaveBean(SampleCachePort::class.java)
                assertThat(context).doesNotHaveBean(RouterFunction::class.java)
            }
    }
}
