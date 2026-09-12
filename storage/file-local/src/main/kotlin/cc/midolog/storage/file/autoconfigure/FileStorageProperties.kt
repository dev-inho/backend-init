package cc.midolog.storage.file.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

@ConfigurationProperties(prefix = "storage.file")
data class FileStorageProperties(
    var provider: String? = null,
    var maxSizeBytes: Long = 10 * 1024 * 1024L,
    var allowedContentTypes: List<String> = emptyList(),
    @NestedConfigurationProperty
    var local: LocalProperties = LocalProperties()
) {
    data class LocalProperties(
        var rootDir: String? = null
    )
}
