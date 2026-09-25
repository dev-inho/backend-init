package cc.midolog.storage.file.autoconfigure

import org.springframework.beans.factory.InitializingBean
import java.nio.file.Paths

/**
 * 파일 스토리지 설정 프로퍼티의 유효성을 애플리케이션 기동 시점에 신속히 검증(Fail-Fast)하는 컴포넌트.
 *
 * 유효하지 않은 프로바이더 설정이나 필수 속성 누락 시 명확한 메시지와 함께 예외를 발생시켜 비정상 기동을 차단한다.
 */
class FileStoragePropertiesValidator(
    private val properties: FileStorageProperties,
) : InitializingBean {

    override fun afterPropertiesSet() {
        val provider = properties.provider
        if (provider.isNullOrBlank() || (provider != "local" && provider != "s3")) {
            throw IllegalStateException("storage.file.provider 필수이며 'local'이어야 합니다. (s3 지원)")
        }

        if (properties.maxSizeBytes <= 0) {
            throw IllegalStateException("storage.file.max-size-bytes 는 0보다 커야 합니다.")
        }

        when (provider) {
            "local" -> validateLocalProperties()
            "s3" -> validateS3Properties()
        }
    }

    private fun validateLocalProperties() {
        val rootDir = properties.local.rootDir
        if (rootDir == null || rootDir.isBlank()) {
            throw IllegalStateException("storage.file.local.root-dir 속성은 필수입니다.")
        }
        val path = Paths.get(rootDir)
        if (!path.isAbsolute) {
            throw IllegalStateException("storage.file.local.root-dir 절대 경로여야 합니다.")
        }
    }

    private fun validateS3Properties() {
        val s3 = properties.s3
        if (s3.bucket.isNullOrBlank()) {
            throw IllegalStateException("storage.file.s3.bucket 속성은 필수입니다.")
        }
        if (s3.region.isBlank()) {
            throw IllegalStateException("storage.file.s3.region 속성은 필수입니다.")
        }
        if (s3.accessKey.isNullOrBlank()) {
            throw IllegalStateException("storage.file.s3.access-key 속성은 필수입니다.")
        }
        if (s3.secretKey.isNullOrBlank()) {
            throw IllegalStateException("storage.file.s3.secret-key 속성은 필수입니다.")
        }
    }
}
