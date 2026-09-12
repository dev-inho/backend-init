package cc.midolog.file.port.storage

import cc.midolog.file.model.StoredFile

/**
 * 파일 영속 저장소와의 저수준 스트리밍 입출력을 담당하는 도메인 포트.
 *
 * 로컬 파일 시스템이나 원격 객체 스토리지와의 실제 I/O를 추상화하며,
 * 청크 기반 스트리밍 저장, 읽기 스트림 로드, 삭제 및 존재 여부 확인 기능을 제공한다.
 */
interface FileStoragePort {
    /**
     * 청크 리더 스트림을 읽어 스토리지에 영속화하고 저장된 파일 도메인 모델을 반환한다.
     *
     * 크기(knownSize)를 미리 아는 경우 지정할 수 있으며 null인 경우 동적 청크 스트리밍으로 처리한다.
     * 체크섬(checksum) 계산 및 기대값(expectedChecksum) 검증 책임은 인프라 어댑터에 있다.
     */
    suspend fun store(
        key: String,
        reader: ChunkReader,
        knownSize: Long?,
        contentType: String,
        expectedChecksum: String?,
    ): StoredFile

    /**
     * 지정된 키의 객체를 읽기 위한 청크 리더를 반환한다.
     * 대상 객체가 존재하지 않으면 null을 반환하고, 권한이나 네트워크 오류 발생 시 예외를 던진다.
     */
    suspend fun load(key: String): ChunkReader?

    /**
     * 지정된 키의 객체 삭제를 수행하며 멱등성을 보장한다.
     * 대상 파일이 스토리지에 이미 존재하지 않더라도 예외 없이 성공(true)을 반환해야 한다.
     */
    suspend fun delete(key: String): Boolean

    /**
     * 스토리지 내에 지정된 키의 객체가 존재하는지 확인하여 여부를 반환한다.
     */
    suspend fun exists(key: String): Boolean
}
