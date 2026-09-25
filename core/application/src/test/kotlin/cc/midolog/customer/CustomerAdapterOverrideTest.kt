package cc.midolog.customer

import cc.midolog.customer.autoconfigure.CustomerAutoConfiguration
import cc.midolog.infra.cache.RedisSampleCacheAdapter
import cc.midolog.infra.cache.SampleCacheAutoConfiguration
import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.cache.SampleCachePort
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

class CustomerAdapterOverrideTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                CustomerAutoConfiguration::class.java,
                SampleCacheAutoConfiguration::class.java,
            ),
        )
        .withBean(ReactiveStringRedisTemplate::class.java, { mock(ReactiveStringRedisTemplate::class.java) })

    class CustomSampleCacheAdapter : SampleCachePort {
        override suspend fun get(id: String): Sample? = null
        override suspend fun put(sample: Sample) {}
    }

    class CacheConsumer(val cachePort: SampleCachePort)

    @Configuration
    class ConsumerConfig {
        @Bean
        fun cacheConsumer(cachePort: SampleCachePort): CacheConsumer = CacheConsumer(cachePort)
    }

    @Configuration
    class CustomerCustomCacheConfig {
        @Bean
        fun customerSampleCachePort(): SampleCachePort = CustomSampleCacheAdapter()
    }

    @Configuration
    class CustomerAcmeDescriptorConfig {
        @Bean
        @CustomerDescriptorMetadata(name = "acme")
        fun customerDescriptor(): CustomerDescriptor = CustomerDescriptor(name = "acme")
    }

    @Configuration
    class CustomerBetaDescriptorConfig {
        @Bean
        @CustomerDescriptorMetadata(name = "beta")
        fun customerDescriptor(): CustomerDescriptor = CustomerDescriptor(name = "beta")
    }

    @Test
    fun `기본 환경에서는 공통 RedisSampleCacheAdapter가 등록되고 소비자 빈에 주입된다`() {
        contextRunner
            .withUserConfiguration(ConsumerConfig::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(SampleCachePort::class.java)
                assertThat(context).hasSingleBean(RedisSampleCacheAdapter::class.java)

                val consumer = context.getBean(CacheConsumer::class.java)
                assertThat(consumer.cachePort).isInstanceOf(RedisSampleCacheAdapter::class.java)
            }
    }

    @Test
    fun `고객이 @Primary 없이 @Bean으로 SampleCachePort를 등록하면 공통 어댑터가 교체되어 고객 어댑터가 소비자 빈에 주입된다`() {
        contextRunner
            .withUserConfiguration(CustomerCustomCacheConfig::class.java, ConsumerConfig::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(SampleCachePort::class.java)
                assertThat(context).doesNotHaveBean(RedisSampleCacheAdapter::class.java)

                val port = context.getBean(SampleCachePort::class.java)
                assertThat(port).isInstanceOf(CustomSampleCacheAdapter::class.java)

                val consumer = context.getBean(CacheConsumer::class.java)
                assertThat(consumer.cachePort).isInstanceOf(CustomSampleCacheAdapter::class.java)
            }
    }

    @Test
    fun `고객 @Bean 어댑터와 소비자 빈이 존재하는 상태에서 app_customer와 CustomerDescriptor가 일치하면 정상 기동된다`() {
        contextRunner
            .withPropertyValues("app.customer=acme")
            .withUserConfiguration(
                CustomerCustomCacheConfig::class.java,
                CustomerAcmeDescriptorConfig::class.java,
                ConsumerConfig::class.java,
            )
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(SampleCachePort::class.java)
                val consumer = context.getBean(CacheConsumer::class.java)
                assertThat(consumer.cachePort).isInstanceOf(CustomSampleCacheAdapter::class.java)
            }
    }

    @Test
    fun `고객 @Bean 어댑터와 소비자 빈이 존재하는 상태에서 app_customer 설정이 있으나 CustomerDescriptor가 누락되면 fail-fast한다`() {
        contextRunner
            .withPropertyValues("app.customer=acme")
            .withUserConfiguration(CustomerCustomCacheConfig::class.java, ConsumerConfig::class.java)
            .run { context ->
                assertThat(context).hasFailed()
                val cause = generateSequence(context.startupFailure) { it.cause }.last()
                assertThat(cause.message)
                    .contains("app.customer")
                    .contains("acme")
            }
    }

    @Test
    fun `고객 @Bean 어댑터와 소비자 빈이 존재하는 상태에서 app_customer 설정과 CustomerDescriptor가 불일치하면 fail-fast한다`() {
        contextRunner
            .withPropertyValues("app.customer=acme")
            .withUserConfiguration(
                CustomerCustomCacheConfig::class.java,
                CustomerBetaDescriptorConfig::class.java,
                ConsumerConfig::class.java,
            )
            .run { context ->
                assertThat(context).hasFailed()
                val cause = generateSequence(context.startupFailure) { it.cause }.last()
                assertThat(cause.message)
                    .contains("acme")
                    .contains("beta")
            }
    }
}
