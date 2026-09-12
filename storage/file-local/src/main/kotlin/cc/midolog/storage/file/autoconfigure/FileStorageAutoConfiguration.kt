package cc.midolog.storage.file.autoconfigure

import cc.midolog.storage.file.local.LocalFileStorageAdapter
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@AutoConfiguration
@EnableConfigurationProperties(FileStorageProperties::class)
class FileStorageAutoConfiguration(
    private val properties: FileStorageProperties
) {
    @Bean
    fun fileStoragePropertiesValidator(): FileStoragePropertiesValidator {
        return FileStoragePropertiesValidator(properties)
    }

    @Bean
    @ConditionalOnProperty(name = ["storage.file.provider"], havingValue = "local")
    fun localFileStorageAdapter(): LocalFileStorageAdapter {
        return LocalFileStorageAdapter(properties)
    }
}
