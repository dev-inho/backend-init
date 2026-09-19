package cc.midolog.storage.file.local

import cc.midolog.storage.file.autoconfigure.FileStorageAutoConfiguration
import cc.midolog.storage.file.autoconfigure.FileStorageProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.DependsOn

/**
 * 로컬 파일 시스템 스토리지 어댑터 자동 설정 클래스.
 *
 * storage.file.provider=local 조건에서 활성화되어 LocalFileStorageAdapter 빈을 등록한다.
 */
@AutoConfiguration(after = [FileStorageAutoConfiguration::class])
@ConditionalOnClass(LocalFileStorageAdapter::class)
@ConditionalOnProperty(name = ["storage.file.provider"], havingValue = "local")
class LocalFileStorageAutoConfiguration(
    private val properties: FileStorageProperties,
) {
    @Bean("fileLocalStorageAdapter")
    @DependsOn("fileStoragePropertiesValidator")
    @ConditionalOnMissingBean(name = ["fileLocalStorageAdapter"])
    fun fileLocalStorageAdapter(): LocalFileStorageAdapter {
        return LocalFileStorageAdapter(properties)
    }
}
