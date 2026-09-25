package cc.midolog.storage.jpa.customer

import cc.midolog.customer.port.repository.CustomerRepositoryPort
import cc.midolog.customers.acme.model.AcmeOrderNote
import cc.midolog.storage.jpa.customers.acme.AcmeOrderNoteJpaAutoConfiguration
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ResolvableType

class JpaCustomerAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withPropertyValues(
            "spring.datasource.url=jdbc:h2:mem:testdb_jpa_customer;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "spring.datasource.driverClassName=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        )
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration::class.java,
                DataSourceTransactionManagerAutoConfiguration::class.java,
                HibernateJpaAutoConfiguration::class.java,
                AcmeOrderNoteJpaAutoConfiguration::class.java,
            ),
        )
        .withUserConfiguration(TestDependenciesConfig::class.java)

    @Test
    fun `registers customer repository port when provider is jpa and customer is acme`() {
        contextRunner
            .withPropertyValues("storage.persistence.provider=jpa", "app.customer=acme")
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
            .withPropertyValues("storage.persistence.provider=jpa", "app.customer=acme")
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
            .withPropertyValues("storage.persistence.provider=mybatis", "app.customer=acme")
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
            .withPropertyValues("app.customer=acme")
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
    fun `does not register port when app customer is different or not set`() {
        contextRunner
            .withPropertyValues("storage.persistence.provider=jpa", "app.customer=corp")
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
            .withPropertyValues("storage.persistence.provider=jpa")
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
            .withPropertyValues("storage.persistence.provider=jpa", "app.customer=acme")
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
        fun transactionOperations(transactionManager: org.springframework.transaction.PlatformTransactionManager): org.springframework.transaction.support.TransactionOperations =
            org.springframework.transaction.support.TransactionTemplate(transactionManager)
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
