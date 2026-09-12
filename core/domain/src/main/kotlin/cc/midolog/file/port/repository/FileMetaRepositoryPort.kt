package cc.midolog.file.port.repository

import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.StoredFile

/**
 * 파일 메타데이터의 영속 저장을 담당하는 도메인 리포지토리 포트 초안.
 *
 * 파일 메타데이터의 식별자 기반 조회, 신규 저장 및 갱신, 상태 전이, 만료된 업로드 대기(PENDING) 건의
 * 일괄 조회를 추상화한다.
 *
 * 이 인터페이스는 Phase 0에서 정의된 초안 규격이며, Phase 2 메타데이터 영속성 트랜잭션 전이 워커가
 * 실제 JPA/MyBatis 영속성 어댑터 구현 및 계약 테스트 결과에 따라 시그니처와 세부 영속 모델을 최종 조정할 수 있다.
 * 따라서 Phase 0에서는 별도의 독자적인 영속 엔티티 모델을 임의로 확정하지 않고 기존 도메인 모델([StoredFile], [FileStatus])을
 * 활용하여 인터페이스 계약을 정의한다.
 */
interface FileMetaRepositoryPort {
    /**
     * 파일 식별자로 저장된 파일 메타데이터를 조회한다. 대상 파일이 없으면 null을 반환한다.
     */
    suspend fun findById(id: String): StoredFile?

    /**
     * 파일 메타데이터를 저장하거나 갱신하고, 저장 완료된 최신 상태의 도메인 모델을 반환한다.
     */
    suspend fun save(file: StoredFile): StoredFile

    /**
     * 지정된 파일 식별자의 파일 상태를 변경하고 성공 여부를 반환한다.
     */
    suspend fun updateStatus(id: String, status: FileStatus): Boolean

    /**
     * 업로드 대기(PENDING) 상태에서 만료된 파일 메타데이터 목록을 최대 지정 건수(limit)만큼 조회한다.
     * 주기적인 고아 객체 정리 작업에서 활용된다.
     */
    suspend fun findExpiredPending(limit: Int): List<StoredFile>
}
