package cc.midolog.file.model

/**
 * 스토리지에 영속화되는 파일의 메타데이터 도메인 모델.
 *
 * 파일 상태가 PENDING(업로드 대기 중)일 때는 크기, 컨텐츠 타입, 체크섬 등이 아직 확정되지 않아 nullable하며,
 * 파일 업로드 만료 등 시간 기반 조회를 위해 생성/수정 시각을 포함한다.
 */
data class FileMeta(
    val id: String,
    val ownerId: String,
    val storageKey: String,
    val sizeBytes: Long?,
    val contentType: String?,
    val checksum: String?,
    val status: FileStatus,
    val createdAt: java.time.Instant,
    val updatedAt: java.time.Instant,
)
