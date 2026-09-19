package cc.midolog.storage.file.local

import cc.midolog.file.port.storage.FileStoragePort
import cc.midolog.storage.file.autoconfigure.FileStorageAutoConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class LocalFileStorageAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            FileStorageAutoConfiguration::class.java,
            LocalFileStorageAutoConfiguration::class.java
        ))

    @Test
    fun `정상 절대 경로가 주어지면 Local 어댑터 빈이 등록된다`() {
        contextRunner.withPropertyValues(
            "storage.file.provider=local",
            "storage.file.local.root-dir=${System.getProperty("user.dir")}/build/files"
        ).run { context ->
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed()
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(LocalFileStorageAdapter::class.java)
            org.assertj.core.api.Assertions.assertThat(context).hasBean("fileLocalStorageAdapter")
        }
    }

    class ExistingLegacyComponentConfig {
        @org.springframework.context.annotation.Bean("localFileStorageAdapter")
        fun localFileStorageAdapter(): String = "legacyComponent"
    }

    @Test
    fun `옛 component와 동일한 bean 이름 localFileStorageAdapter가 존재해도 충돌 없이 컨텍스트가 로드된다`() {
        contextRunner.withUserConfiguration(ExistingLegacyComponentConfig::class.java)
            .withPropertyValues(
                "storage.file.provider=local",
                "storage.file.local.root-dir=${System.getProperty("user.dir")}/build/files"
            ).run { context ->
                org.assertj.core.api.Assertions.assertThat(context).hasNotFailed()
                org.assertj.core.api.Assertions.assertThat(context).hasBean("fileLocalStorageAdapter")
                org.assertj.core.api.Assertions.assertThat(context).hasBean("localFileStorageAdapter")
            }
    }

    @org.springframework.context.annotation.Configuration
    @org.springframework.context.annotation.ComponentScan("cc.midolog.storage.file.local")
    class TestScanConfig

    @Test
    fun `컴포넌트 스캔과 자동 설정이 겹쳐도 FileStoragePort 빈은 하나만 생성된다`() {
        contextRunner.withUserConfiguration(TestScanConfig::class.java)
            .withPropertyValues(
                "storage.file.provider=local",
                "storage.file.local.root-dir=${System.getProperty("user.dir")}/build/files"
            ).run { context ->
                org.assertj.core.api.Assertions.assertThat(context).hasNotFailed()
                org.assertj.core.api.Assertions.assertThat(context.getBeansOfType(FileStoragePort::class.java)).hasSize(1)
            }
    }

    @Test
    fun `provider가 s3이면 LocalFileStorageAdapter는 등록되지 않는다`() {
        contextRunner.withPropertyValues(
            "storage.file.provider=s3",
            "storage.file.s3.bucket=test-bucket",
            "storage.file.s3.region=us-east-1",
            "storage.file.s3.access-key=dummy-key",
            "storage.file.s3.secret-key=dummy-secret"
        ).run { context ->
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed()
            org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean("fileLocalStorageAdapter")
        }
    }
}
