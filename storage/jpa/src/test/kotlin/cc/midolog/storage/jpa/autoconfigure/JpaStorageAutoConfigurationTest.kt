package cc.midolog.storage.jpa.autoconfigure

import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPort
import cc.midolog.storage.jpa.file.JpaFileMetaRepositoryAdapter
import cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter
import cc.midolog.storage.jpa.user.JpaUserRepositoryAdapter
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class JpaStorageAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withPropertyValues(
            "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
            "spring.datasource.driverClassName=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
        )
        .withConfiguration(
            AutoConfigurations.of(
                DataSourceAutoConfiguration::class.java,
                DataSourceTransactionManagerAutoConfiguration::class.java,
                HibernateJpaAutoConfiguration::class.java,
                JpaStorageAutoConfiguration::class.java,
                JpaProviderValidationAutoConfiguration::class.java
            )
        )

    @Test
    fun `provider가 jpa일 때 자동 구성이 활성화되어 3개의 어댑터가 등록된다`() {
        contextRunner.withPropertyValues("storage.persistence.provider=jpa")
            .run { context ->
                assertThat(context).hasNotFailed()
                assertThat(context).hasSingleBean(SampleRepositoryPort::class.java)
                assertThat(context).hasSingleBean(UserRepositoryPort::class.java)
                assertThat(context).hasSingleBean(FileMetaRepositoryPort::class.java)

                assertThat(context.getBean(SampleRepositoryPort::class.java)).isInstanceOf(JpaSampleRepositoryAdapter::class.java)
                assertThat(context.getBean(UserRepositoryPort::class.java)).isInstanceOf(JpaUserRepositoryAdapter::class.java)
                assertThat(context.getBean(FileMetaRepositoryPort::class.java)).isInstanceOf(JpaFileMetaRepositoryAdapter::class.java)
            }
    }

    @Test
    fun `provider가 jpa가 아닐 때(mybatis) 포트 빈이 등록되지 않는다`() {
        contextRunner.withPropertyValues("storage.persistence.provider=mybatis")
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
            val cause = context.startupFailure!!.cause
            assertThat(cause!!.message).contains("jpa").contains("mybatis")
        }

        // Typo
        contextRunner.withPropertyValues("storage.persistence.provider=jppa").run { context ->
            assertThat(context).hasFailed()
            val cause = context.startupFailure!!.cause
            assertThat(cause!!.message).contains("jpa").contains("mybatis")
        }
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
            .withPropertyValues("storage.persistence.provider=jpa")
            .run { context ->
                assertThat(context).hasNotFailed()
                val beans = context.getBeansOfType(SampleRepositoryPort::class.java)
                assertThat(beans).hasSize(1)
                assertThat(beans.values.first()).isNotInstanceOf(JpaSampleRepositoryAdapter::class.java)
            }
    }
}
