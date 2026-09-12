package cc.midolog.storage.file.autoconfigure

import org.springframework.beans.factory.InitializingBean

class FileStoragePropertiesValidator(
    private val properties: FileStorageProperties
) : InitializingBean {
    override fun afterPropertiesSet() {
        if (properties.provider != "local") {
            throw IllegalStateException("storage.file.provider 필수이며 'local'이어야 합니다.")
        }
        val rootDir = properties.local.rootDir
        if (rootDir == null || rootDir.isBlank()) {
            throw IllegalStateException("storage.file.local.root-dir 속성은 필수입니다.")
        }
        val path = java.nio.file.Paths.get(rootDir)
        if (!path.isAbsolute) {
            throw IllegalStateException("storage.file.local.root-dir 절대 경로여야 합니다.")
        }
        if (properties.maxSizeBytes <= 0) {
            throw IllegalStateException("storage.file.max-size-bytes 는 0보다 커야 합니다.")
        }
    }
}
