package cc.midolog.storage.mybatis.customer

import cc.midolog.customer.port.repository.CustomerRepositoryPort
import cc.midolog.customers.acme.model.AcmeOrderNote
import cc.midolog.storage.mybatis.customers.acme.AcmeOrderNoteMyBatisAutoConfiguration
import org.apache.ibatis.session.Configuration as MyBatisConfiguration
import org.apache.ibatis.session.SqlSessionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.FilteredClassLoader
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.ResolvableType

class MyBatisCustomerAutoConfigurationTest {

    @Configuration(proxyBeanMethods = false)
    class TestDependenciesConfig {
        @Bean
        fun sqlSessionFactory(): SqlSessionFactory {
            val factory = mock(SqlSessionFactory::class.java)
            val config = MyBatisConfiguration()
            config.environment = org.apache.ibatis.mapping.Environment(
                "test",
                org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
                mock(javax.sql.DataSource::class.java),
            )
            `when`(factory.configuration).thenReturn(config)
            return factory
        }
    }

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AcmeOrderNoteMyBatisAutoConfiguration::class.java))
        .withUserConfiguration(TestDependenciesConfig::class.java)

    @Test
    fun `registers customer repository port when provider is mybatis and customer is acme`() {
        contextRunner
            .withPropertyValues("storage.persistence.provider=mybatis", "app.customer=acme")
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
            .withPropertyValues("storage.persistence.provider=mybatis", "app.customer=acme")
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
    fun `does not register port when provider is jpa or not set`() {
        contextRunner
            .withPropertyValues("storage.persistence.provider=jpa", "app.customer=acme")
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
            .withPropertyValues("storage.persistence.provider=mybatis", "app.customer=corp")
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
    }

    @Test
    fun `backs off when customer class is not on classpath`() {
        contextRunner
            .withPropertyValues("storage.persistence.provider=mybatis", "app.customer=acme")
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
