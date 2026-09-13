package cc.midolog.storage.mybatis.autoconfigure

import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPort
import cc.midolog.storage.mybatis.file.MyBatisFileMetaRepositoryAdapter
import cc.midolog.storage.mybatis.sample.MyBatisSampleRepositoryAdapter
import cc.midolog.storage.mybatis.user.MyBatisUserRepositoryAdapter
import org.apache.ibatis.session.Configuration as MyBatisConfiguration
import org.apache.ibatis.session.SqlSessionFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class MyBatisStorageAutoConfigurationTest {

    @Configuration
    class MockConfig {
        @Bean fun sqlSessionFactory(): SqlSessionFactory {
            val factory = mock(SqlSessionFactory::class.java)
            val config = MyBatisConfiguration()
            config.environment = org.apache.ibatis.mapping.Environment("test", org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(), mock(javax.sql.DataSource::class.java))
            `when`(factory.configuration).thenReturn(config)
            return factory
        }
    }

    private val contextRunner = ApplicationContextRunner()
        .withUserConfiguration(MockConfig::class.java)
        .withConfiguration(AutoConfigurations.of(MyBatisStorageAutoConfiguration::class.java, MyBatisProviderValidationAutoConfiguration::class.java))

    @Test
    fun `provider가 mybatis일 때 자동 구성이 활성화되어 3개의 어댑터가 등록된다`() {
        contextRunner.withPropertyValues("storage.persistence.provider=mybatis")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(SampleRepositoryPort::class.java)
                assertThat(context).hasSingleBean(UserRepositoryPort::class.java)
                assertThat(context).hasSingleBean(FileMetaRepositoryPort::class.java)

                assertThat(context.getBean(SampleRepositoryPort::class.java)).isInstanceOf(MyBatisSampleRepositoryAdapter::class.java)
                assertThat(context.getBean(UserRepositoryPort::class.java)).isInstanceOf(MyBatisUserRepositoryAdapter::class.java)
                assertThat(context.getBean(FileMetaRepositoryPort::class.java)).isInstanceOf(MyBatisFileMetaRepositoryAdapter::class.java)
            }
    }

    @Test
    fun `provider가 mybatis가 아닐 때(jpa) 포트 빈이 등록되지 않는다`() {
        contextRunner.withPropertyValues("storage.persistence.provider=jpa")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context.getBeansOfType(SampleRepositoryPort::class.java)).isEmpty()
                assertThat(context.getBeansOfType(UserRepositoryPort::class.java)).isEmpty()
                assertThat(context.getBeansOfType(FileMetaRepositoryPort::class.java)).isEmpty()
            }
    }

    @Test
    fun `provider가 누락되거나 오타일 때 기동 실패해야 한다`() {
        // Missing
        contextRunner.run { context ->
            assertThat(context).hasFailed()
            val cause = context.startupFailure!!.let { it.cause ?: it }
            assertThat(cause.message).contains("jpa").contains("mybatis")
        }

        // Typo
        contextRunner.withPropertyValues("storage.persistence.provider=mybatus").run { context ->
            assertThat(context).hasFailed()
            val cause = context.startupFailure!!.let { it.cause ?: it }
            assertThat(cause.message).contains("jpa").contains("mybatis")
        }
    }

    @Test
    fun `MyBatisProviderValidationAutoConfiguration의 BeanFactoryPostProcessor 빈 메서드는 static 바이트코드로 노출되어야 한다`() {
        val beanMethods = MyBatisProviderValidationAutoConfiguration::class.java.methods
            .filter { it.isAnnotationPresent(Bean::class.java) }

        assertThat(beanMethods).isNotEmpty
        assertThat(beanMethods).allMatch { java.lang.reflect.Modifier.isStatic(it.modifiers) }
    }

    @Configuration
    class ConsumerConfig {
        @Bean
        fun customSampleRepositoryPort(): SampleRepositoryPort {
            return object : SampleRepositoryPort {
                override suspend fun save(sample: cc.midolog.sample.model.Sample): cc.midolog.sample.model.Sample = sample
                override suspend fun findById(id: String): cc.midolog.sample.model.Sample? = null
            }
        }
    }

    class ConsumerService(val sampleRepositoryPort: SampleRepositoryPort)

    @Test
    fun `provider 미설정 시 SampleRepositoryPort를 요구하는 소비자 빈이 있더라도 NoSuchBeanDefinitionException 전에 provider 검증 실패가 발생해야 한다`() {
        contextRunner.withUserConfiguration(ConsumerService::class.java)
            .run { context ->
                assertThat(context).hasFailed()
                val failure = context.startupFailure
                assertThat(failure).isNotNull

                val causes = generateSequence(failure) { it.cause }.toList()
                assertThat(causes)
                    .`as`("원인 chain에 NoSuchBeanDefinitionException이 존재하지 않아야 한다")
                    .noneMatch { it is org.springframework.beans.factory.NoSuchBeanDefinitionException }

                assertThat(causes.any { cause ->
                    val msg = cause.message ?: ""
                    msg.contains("storage.persistence.provider") && msg.contains("jpa") && msg.contains("mybatis")
                })
                    .`as`("원인 chain에 storage.persistence.provider, jpa, mybatis가 모두 포함된 검증 예외가 존재해야 한다")
                    .isTrue()
            }
    }

    @Test
    fun `소비자가 SampleRepositoryPort 빈을 선등록하면 기본 어댑터는 물러난다`() {
        contextRunner.withUserConfiguration(ConsumerConfig::class.java)
            .withPropertyValues("storage.persistence.provider=mybatis")
            .run { context ->
                assertThat(context).hasNotFailed()
                val beans = context.getBeansOfType(SampleRepositoryPort::class.java)
                assertThat(beans).hasSize(1)
                assertThat(beans.values.first()).isNotInstanceOf(MyBatisSampleRepositoryAdapter::class.java)
            }
    }

    @Test
    fun `미지원 데이터베이스 벤더 감지 시 fail-fast 예외가 발생해야 한다`() {
        val autoConfig = MyBatisStorageAutoConfiguration()
        val provider = autoConfig.databaseIdProvider()

        val mockDataSource = mock(javax.sql.DataSource::class.java)
        val mockConnection = mock(java.sql.Connection::class.java)
        val mockMetaData = mock(java.sql.DatabaseMetaData::class.java)

        `when`(mockDataSource.connection).thenReturn(mockConnection)
        `when`(mockConnection.metaData).thenReturn(mockMetaData)
        `when`(mockMetaData.databaseProductName).thenReturn("Oracle")

        org.junit.jupiter.api.assertThrows<IllegalStateException> {
            provider.getDatabaseId(mockDataSource)
        }.also { ex ->
            assertThat(ex.message).contains("지원 벤더: postgresql, h2 — 감지: Oracle")
        }
    }

}
