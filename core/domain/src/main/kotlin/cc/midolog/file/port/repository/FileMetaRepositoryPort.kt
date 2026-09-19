package cc.midolog.file.port.repository

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import java.time.Instant

/**
 * 파일 메타데이터의 영속 저장을 담당하는 도메인 리포지토리 포트.
 *
 * 파일 메타데이터의 식별자 기반 조회, 신규 저장 및 갱신, 원자적 상태 전이, 만료된 고아 객체의
 * 일괄 조회를 추상화한다.
 */
interface FileMetaRepositoryPort {
    /**
     * 파일 식별자로 저장된 파일 메타데이터를 조회한다. 대상 파일이 없으면 null을 반환한다.
     */
    suspend fun findById(id: String): FileMeta?

    /**
     * 파일 메타데이터를 저장하거나 갱신하고, 저장 완료된 최신 상태의 도메인 모델을 반환한다.
     */
    suspend fun save(file: FileMeta): FileMeta

    /**
     * 지정된 파일 식별자의 파일 상태를 변경하고 성공 여부를 반환한다.
     * updateStatus는 존재하지 않는 id일 경우 false를 반환하고, 성공 1행일 경우 true를 반환한다.
     */
    suspend fun updateStatus(id: String, status: FileStatus): Boolean

    /**
     * 지정된 파일의 현재 상태가 expectedStatuses 중 하나와 일치할 때만 새로운 상태(newStatus)로 원자적으로 전이한다.
     * 선택적으로 크기, 콘텐츠 타입, 체크섬을 함께 갱신할 수 있다.
     * 상태 전이에 성공하면 true, 현재 상태가 기대 상태와 달라 전이되지 않았으면 false를 반환한다.
     * 동시 finalize/delete 경합 시 DELETED 상태가 READY로 부활하지 않도록 데이터베이스 원자성 수준에서 보장한다.
     */
    suspend fun updateStatusConditionally(
        id: String,
        expectedStatuses: Set<FileStatus>,
        newStatus: FileStatus,
        sizeBytes: Long? = null,
        contentType: String? = null,
        checksum: String? = null,
    ): Boolean = updateStatus(id, newStatus)

    /**
     * 업로드 대기(PENDING) 상태에서 지정된 시각(cutoff) 이전에 업데이트된 만료된 파일 메타데이터 목록을
     * 최대 지정 건수(limit)만큼 updatedAt 오름차순으로 정렬하여 조회한다.
     */
    suspend fun findExpiredPending(cutoff: Instant, limit: Int): List<FileMeta>

    /**
     * 지정된 상태(statuses)이면서 지정된 시각(cutoff) 이전에 업데이트된 만료 파일 메타데이터 목록을
     * 최대 지정 건수(limit)만큼 updatedAt 오름차순으로 정렬하여 조회한다.
     * PENDING 상태의 미완료 파일뿐 아니라 삭제 실패로 남은 FAILED 고아 객체도 함께 회수할 수 있도록 지원한다.
     */
    suspend fun findExpiredOrphans(
        cutoff: Instant,
        limit: Int,
        statuses: Set<FileStatus> = setOf(FileStatus.PENDING, FileStatus.FAILED),
    ): List<FileMeta> = findExpiredPending(cutoff, limit)
}
