package cc.midolog.file.port.repository

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus

/**
 * 파일 메타데이터의 영속 저장을 담당하는 도메인 리포지토리 포트.
 *
 * 파일 메타데이터의 식별자 기반 조회, 신규 저장 및 갱신, 상태 전이, 만료된 업로드 대기(PENDING) 건의
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
     * 업로드 대기(PENDING) 상태에서 지정된 시각(cutoff) 이전에 업데이트된 만료된 파일 메타데이터 목록을 
     * 최대 지정 건수(limit)만큼 updatedAt 오름차순으로 정렬하여 조회한다.
     * 주기적인 고아 객체 정리 작업에서 활용된다.
     */
    suspend fun findExpiredPending(cutoff: java.time.Instant, limit: Int): List<FileMeta>
}
