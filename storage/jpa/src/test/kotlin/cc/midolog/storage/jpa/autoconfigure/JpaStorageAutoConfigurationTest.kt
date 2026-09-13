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
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class JpaStorageAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(JpaStorageAutoConfiguration::class.java))

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
            assertThat(context.startupFailure).hasMessageContaining("jpa")
            assertThat(context.startupFailure).hasMessageContaining("mybatis")
        }

        // Typo
        contextRunner.withPropertyValues("storage.persistence.provider=jppa").run { context ->
            assertThat(context).hasFailed()
            assertThat(context.startupFailure).hasMessageContaining("jpa")
            assertThat(context.startupFailure).hasMessageContaining("mybatis")
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
