package cc.midolog.storage.file.s3

import cc.midolog.file.port.storage.FilePresignPort
import cc.midolog.file.port.storage.FileStoragePort
import cc.midolog.storage.file.autoconfigure.FileStorageAutoConfiguration
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner

class S3FileStorageAutoConfigurationTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            FileStorageAutoConfiguration::class.java,
            S3FileStorageAutoConfiguration::class.java
        ))

    @Test
    fun `provider가 s3이고 필수 설정이 주어지면 S3 빈들이 등록된다`() {
        contextRunner.withPropertyValues(
            "storage.file.provider=s3",
            "storage.file.s3.bucket=my-bucket",
            "storage.file.s3.region=us-east-1",
            "storage.file.s3.access-key=dummy-access",
            "storage.file.s3.secret-key=dummy-secret",
            "storage.file.s3.endpoint=http://localhost:9000"
        ).run { context ->
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed()
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(S3FileStorageAdapter::class.java)
            org.assertj.core.api.Assertions.assertThat(context).hasSingleBean(S3FilePresignAdapter::class.java)
            org.assertj.core.api.Assertions.assertThat(context).hasBean("fileS3StorageAdapter")
            org.assertj.core.api.Assertions.assertThat(context).hasBean("fileS3PresignAdapter")

            val storagePort = context.getBean(FileStoragePort::class.java)
            org.assertj.core.api.Assertions.assertThat(storagePort).isInstanceOf(S3FileStorageAdapter::class.java)

            val presignPort = context.getBean(FilePresignPort::class.java)
            org.assertj.core.api.Assertions.assertThat(presignPort.isSupported).isTrue()
        }
    }

    @Test
    fun `provider가 local이면 S3 빈들은 등록되지 않는다`() {
        contextRunner.withPropertyValues(
            "storage.file.provider=local",
            "storage.file.local.root-dir=${System.getProperty("user.dir")}/build/files"
        ).run { context ->
            org.assertj.core.api.Assertions.assertThat(context).hasNotFailed()
            org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean("fileS3StorageAdapter")
            org.assertj.core.api.Assertions.assertThat(context).doesNotHaveBean("fileS3PresignAdapter")
        }
    }
}
