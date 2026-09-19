package cc.midolog.storage.file.autoconfigure

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean

/**
 * 파일 스토리지 인프라 모듈의 공통 자동 설정 클래스.
 *
 * 파일 스토리지 프로퍼티를 등록하고 기동 시점 유효성 검증(Fail-fast)을 수행하는 검증기 빈을 구성한다.
 */
@AutoConfiguration
@EnableConfigurationProperties(FileStorageProperties::class)
class FileStorageAutoConfiguration(
    private val properties: FileStorageProperties,
) {
    @Bean
    @ConditionalOnMissingBean
    fun fileStoragePropertiesValidator(): FileStoragePropertiesValidator {
        return FileStoragePropertiesValidator(properties)
    }
}
