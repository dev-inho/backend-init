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

    @Test
    fun `상대 경로 root-dir은 기동 실패해야 한다`() {
        contextRunner.withPropertyValues(
            "storage.file.provider=local",
            "storage.file.local.root-dir=./relative/path"
        ).run { context ->
            org.assertj.core.api.Assertions.assertThat(context).hasFailed()
            org.assertj.core.api.Assertions.assertThat(context.startupFailure)
                .hasMessageContaining("절대 경로여야 합니다")
        }
    }

    @Test
    fun `정상 절대 경로가 주어지면 validator와 adapter 빈이 생성되고 기본값이 적용된다`() {
        contextRunner.withPropertyValues(
            "storage.file.provider=local",
            "storage.file.local.root-dir=${System.getProperty("user.dir")}/build/files"
        ).run { context ->
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed()
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(FileStoragePropertiesValidator::class.java)
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(cc.midolog.storage.file.local.LocalFileStorageAdapter::class.java)

            val props = context.getBean(FileStorageProperties::class.java)
            org.assertj.core.api.Assertions.assertThat(props.maxSizeBytes).isEqualTo(10 * 1024 * 1024L)
            org.assertj.core.api.Assertions.assertThat(props.allowedContentTypes).isEmpty()
        }
    }

    @Test
    fun `META-INF imports 파일 내용이 정확해야 한다`() {
        val url = javaClass.classLoader.getResource("META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")
        org.assertj.core.api.Assertions.assertThat(url).isNotNull
        val content = url!!.readText().trim()
        org.assertj.core.api.Assertions.assertThat(content).isEqualTo("cc.midolog.storage.file.autoconfigure.FileStorageAutoConfiguration")
    }
}
