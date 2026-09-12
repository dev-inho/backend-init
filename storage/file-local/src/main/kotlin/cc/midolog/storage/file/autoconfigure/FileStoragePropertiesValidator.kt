package cc.midolog.storage.file.autoconfigure

import org.springframework.beans.factory.InitializingBean
import java.nio.file.Paths

/**
 * FileStorageProperties의 유효성을 검사하는 검증기.
 * Spring Boot 기동 시점에 Fail-fast를 보장한다.
 */
class FileStoragePropertiesValidator(
    private val properties: FileStorageProperties
) : InitializingBean {
    override fun afterPropertiesSet() {
        require(properties.provider == "local") {
            "storage.file.provider 속성은 필수이며 'local'이어야 합니다. (현재: ${properties.provider})"
        }
        
        val rootDir = properties.local.rootDir
        require(!rootDir.isNullOrBlank()) {
            "storage.file.local.root-dir 속성은 필수입니다."
        }
        
        val path = Paths.get(rootDir)
        require(path.isAbsolute) {
            "storage.file.local.root-dir은 절대 경로여야 합니다. (현재: $rootDir)"
        }
    }
}
