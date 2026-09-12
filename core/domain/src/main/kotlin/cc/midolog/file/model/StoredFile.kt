package cc.midolog.file.model

/**
 * 스토리지에 영속화되었거나 영속 대상인 파일의 메타데이터 도메인 모델.
 *
 * 비즈니스 도메인 식별자(id)와 인가 검증을 위한 소유자 식별자(ownerId),
 * 경로 순회 공격을 방지하기 위해 생성된 무작위 UUID 기반 저장소 키(storageKey),
 * 확정된 파일 크기(sizeBytes), 콘텐츠 타입(contentType),
 * 인프라 계층에서 계산·검증된 체크섬 해시(checksum), 현재 파일 상태(status)를 캡슐화한다.
 */
data class StoredFile(
    val id: String,
    val ownerId: String,
    val storageKey: String,
    val sizeBytes: Long,
    val contentType: String,
    val checksum: String?,
    val status: FileStatus,
)
