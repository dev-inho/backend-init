package cc.midolog.file.model

/**
 * 스토리지 백엔드(S3 HEAD 또는 로컬 파일 시스템 메타데이터)에서 조회한 실제 객체의 도메인 메타데이터 모델.
 *
 * 클라이언트가 주장하는 크기나 체크섬 대신 스토리지에 실제 저장된 객체의 속성을 사후 검증할 때 사용한다.
 *
 * @property sizeBytes 실제 저장소에 기록된 파일의 크기(바이트)
 * @property contentType 실제 저장소 객체의 MIME 미디어 타입
 * @property checksum 저장소 객체에 계산되었거나 메타데이터에 기록된 체크섬 해시값 (예: SHA-256)
 * @property eTag 스토리지 제공자가 부여한 엔터티 태그(ETag)
 */
data class FileMetadata(
    val sizeBytes: Long,
    val contentType: String?,
    val checksum: String? = null,
    val eTag: String? = null,
)
