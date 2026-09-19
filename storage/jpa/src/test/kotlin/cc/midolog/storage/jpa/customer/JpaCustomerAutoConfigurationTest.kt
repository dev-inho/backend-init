package cc.midolog.storage.jpa.customer

import cc.midolog.customer.port.repository.CustomerRepositoryPort
import cc.midolog.customers.acme.model.AcmeOrderNote
import cc.midolog.storage.jpa.customers.acme.AcmeOrderNoteJpaRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ResolvableType
import org.springframework.transaction.support.TransactionOperations

class JpaCustomerAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JpaCustomerAutoConfiguration::class.java))
        .withUserConfiguration(TestDependenciesConfig::class.java)

    @Test
    fun `registers customer repository port when provider is jpa`() {
        contextRunner
            .withPropertyValues("storage.persistence.provider=jpa")
            .run { context ->
                assertThat(context).hasNotFailed()
                val portType = ResolvableType.forClassWithGenerics(
                    CustomerRepositoryPort::class.java,
                    AcmeOrderNote::class.java,
                    String::class.java,
                )
                val beanNames = context.getBeanNamesForType(portType)
                assertThat(beanNames).contains("acmeOrderNoteCustomerRepositoryPort")
            }
    }

    @Test
    fun `backs off when custom repository port is already registered`() {
        contextRunner
            .withPropertyValues("storage.persistence.provider=jpa")
            .withUserConfiguration(CustomPortConfig::class.java)
            .run { context ->
                assertThat(context).hasNotFailed()
                val portType = ResolvableType.forClassWithGenerics(
                    CustomerRepositoryPort::class.java,
                    AcmeOrderNote::class.java,
                    String::class.java,
                )
                val beanNames = context.getBeanNamesForType(portType)
                assertThat(beanNames).containsExactly("acmeOrderNoteCustomerRepositoryPort")
                assertThat(context.getBean("acmeOrderNoteCustomerRepositoryPort")).isSameAs(CustomPortConfig.customPort)
            }
    }

    @Test
    fun `does not register port when provider is mybatis or not set`() {
        contextRunner
            .withPropertyValues("storage.persistence.provider=mybatis")
            .run { context ->
                assertThat(context).hasNotFailed()
                val portType = ResolvableType.forClassWithGenerics(
                    CustomerRepositoryPort::class.java,
                    AcmeOrderNote::class.java,
                    String::class.java,
                )
                assertThat(context.getBeanNamesForType(portType)).isEmpty()
            }

        contextRunner
            .run { context ->
                assertThat(context).hasNotFailed()
                val portType = ResolvableType.forClassWithGenerics(
                    CustomerRepositoryPort::class.java,
                    AcmeOrderNote::class.java,
                    String::class.java,
                )
                assertThat(context.getBeanNamesForType(portType)).isEmpty()
            }
    }

    @Test
    fun `backs off when customer class is not on classpath`() {
        contextRunner
            .withPropertyValues("storage.persistence.provider=jpa")
            .withClassLoader(FilteredClassLoader(AcmeOrderNote::class.java))
            .run { context ->
                assertThat(context).hasNotFailed()
                val portType = ResolvableType.forClassWithGenerics(
                    CustomerRepositoryPort::class.java,
                    AcmeOrderNote::class.java,
                    String::class.java,
                )
                assertThat(context.getBeanNamesForType(portType)).isEmpty()
            }
    }

    @Configuration(proxyBeanMethods = false)
    class TestDependenciesConfig {
        @Bean
        fun acmeOrderNoteJpaRepository(): AcmeOrderNoteJpaRepository = mock(AcmeOrderNoteJpaRepository::class.java)

        @Bean
        fun transactionOperations(): TransactionOperations = mock(TransactionOperations::class.java)
    }

    @Configuration(proxyBeanMethods = false)
    class CustomPortConfig {
        companion object {
            val customPort: CustomerRepositoryPort<AcmeOrderNote, String> = object : CustomerRepositoryPort<AcmeOrderNote, String> {
                override suspend fun findById(id: String): AcmeOrderNote? = null
                override suspend fun save(entity: AcmeOrderNote): AcmeOrderNote = entity
                override suspend fun deleteById(id: String): Boolean = true
            }
        }

        @Bean(name = ["acmeOrderNoteCustomerRepositoryPort"])
        fun acmeOrderNoteCustomerRepositoryPort(): CustomerRepositoryPort<AcmeOrderNote, String> = customPort
    }
}
