package cc.midolog.storage.file.autoconfigure

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty

/**
 * 파일 스토리지 인프라 모듈의 통합 설정 프로퍼티.
 *
 * 지원되는 프로바이더(local, s3)와 최대 허용 크기, 허용 콘텐츠 타입 목록,
 * 그리고 프로바이더별 세부 설정(로컬 루트 디렉터리, S3 엔드포인트/버킷/자격증명 등)을 바인딩한다.
 *
 * @property provider 활성화할 파일 스토리지 프로바이더 식별자 ("local" 또는 "s3")
 * @property maxSizeBytes 파일 업로드 시 허용되는 최대 크기(바이트). 기본값 10MB.
 * @property allowedContentTypes 업로드 허용 MIME 미디어 타입 목록. 비어있으면 모든 타입 허용.
 * @property local 로컬 파일 시스템 스토리지 프로바이더 전용 설정
 * @property s3 AWS S3 및 호환 객체 스토리지(MinIO 등) 프로바이더 전용 설정
 */
@ConfigurationProperties(prefix = "storage.file")
data class FileStorageProperties(
    var provider: String? = null,
    var maxSizeBytes: Long = 10 * 1024 * 1024L,
    var allowedContentTypes: List<String> = emptyList(),
    @NestedConfigurationProperty
    var local: LocalProperties = LocalProperties(),
    @NestedConfigurationProperty
    var s3: S3Properties = S3Properties(),
) {
    /**
     * 로컬 파일 시스템 스토리지 설정 프로퍼티.
     *
     * @property rootDir 파일이 영속화될 서버 로컬의 절대 파일 시스템 디렉터리 경로
     */
    data class LocalProperties(
        var rootDir: String? = null,
    )

    /**
     * S3 및 MinIO 호환 객체 스토리지 설정 프로퍼티.
     *
     * @property endpoint 커스텀 S3 엔드포인트 URL (MinIO 로컬 테스트 시 필수)
     * @property region AWS 리전 식별자 (기본값: "us-east-1")
     * @property bucket 대상 S3 버킷 이름
     * @property accessKey S3 접근 키 식별자
     * @property secretKey S3 비밀 접근 키
     * @property pathStyleAccessEnabled 경로 스타일(Path-style) 버킷 접근 활성화 여부 (MinIO 기본 true)
     */
    data class S3Properties(
        var endpoint: String? = null,
        var region: String = "us-east-1",
        var bucket: String? = null,
        var accessKey: String? = null,
        var secretKey: String? = null,
        var pathStyleAccessEnabled: Boolean = true,
    )
}
