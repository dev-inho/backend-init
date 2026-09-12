package cc.midolog.storage.file.autoconfigure

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class FileStorageAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(FileStorageAutoConfiguration::class.java))

    @Test
    fun `provider가 누락되면 기동 실패해야 한다`() {
        contextRunner.run { context ->
            org.assertj.core.api.Assertions.assertThat(context).hasFailed()
            org.assertj.core.api.Assertions.assertThat(context.startupFailure)
                .hasMessageContaining("필수이며 'local'이어야 합니다")
        }
    }

    @Test
    fun `provider가 오타면 기동 실패해야 한다`() {
        contextRunner.withPropertyValues("storage.file.provider=loccal")
            .run { context ->
                org.assertj.core.api.Assertions.assertThat(context).hasFailed()
                org.assertj.core.api.Assertions.assertThat(context.startupFailure)
                    .hasMessageContaining("필수이며 'local'이어야 합니다")
            }
    }
}
