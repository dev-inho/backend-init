package cc.midolog.storage.file.s3

import cc.midolog.storage.file.autoconfigure.FileStorageAutoConfiguration
import cc.midolog.storage.file.autoconfigure.FileStorageProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.DependsOn
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

/**
 * AWS S3 및 호환 스토리지 어댑터 자동 설정 클래스.
 *
 * storage.file.provider=s3 조건에서 활성화되어 비동기 S3 클라이언트, Presigner,
 * 그리고 FileStoragePort 및 FilePresignPort 구현체 빈을 등록한다.
 */
@AutoConfiguration(after = [FileStorageAutoConfiguration::class])
@ConditionalOnProperty(name = ["storage.file.provider"], havingValue = "s3")
class S3FileStorageAutoConfiguration(
    private val properties: FileStorageProperties,
) {
    @Bean
    @DependsOn("fileStoragePropertiesValidator")
    @ConditionalOnClass(S3AsyncClient::class)
    @ConditionalOnMissingBean
    fun s3AsyncClient(): S3AsyncClient {
        val s3 = properties.s3
        val builder = S3AsyncClient.builder()
            .region(Region.of(s3.region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey, s3.secretKey)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(s3.pathStyleAccessEnabled).build())

        if (!s3.endpoint.isNullOrBlank()) {
            builder.endpointOverride(URI.create(s3.endpoint))
        }
        return builder.build()
    }

    @Bean
    @DependsOn("fileStoragePropertiesValidator")
    @ConditionalOnClass(S3Presigner::class)
    @ConditionalOnMissingBean
    fun s3Presigner(): S3Presigner {
        val s3 = properties.s3
        val builder = S3Presigner.builder()
            .region(Region.of(s3.region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(s3.accessKey, s3.secretKey)))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(s3.pathStyleAccessEnabled).build())

        if (!s3.endpoint.isNullOrBlank()) {
            builder.endpointOverride(URI.create(s3.endpoint))
        }
        return builder.build()
    }

    @Bean("fileS3StorageAdapter")
    @DependsOn("fileStoragePropertiesValidator")
    @ConditionalOnClass(S3FileStorageAdapter::class)
    @ConditionalOnMissingBean(name = ["fileS3StorageAdapter", "fileStoragePort"])
    fun fileS3StorageAdapter(s3AsyncClient: S3AsyncClient): S3FileStorageAdapter {
        return S3FileStorageAdapter(s3AsyncClient, properties.s3.bucket!!)
    }

    @Bean("fileS3PresignAdapter")
    @DependsOn("fileStoragePropertiesValidator")
    @ConditionalOnClass(S3FilePresignAdapter::class)
    @ConditionalOnMissingBean(name = ["fileS3PresignAdapter", "filePresignPort"])
    fun fileS3PresignAdapter(s3Presigner: S3Presigner): S3FilePresignAdapter {
        return S3FilePresignAdapter(s3Presigner, properties.s3.bucket!!)
    }
}
