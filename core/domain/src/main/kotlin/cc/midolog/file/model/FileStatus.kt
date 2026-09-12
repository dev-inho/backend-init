package cc.midolog.file.model

/**
 * 파일의 라이프사이클 상태를 나타내는 열거형.
 *
 * 파일 업로드 준비(PENDING) 단계부터 물리 저장 및 검증 완료(READY),
 * 처리 실패(FAILED), 논리/물리 삭제(DELETED) 단계까지의 전이를 정의한다.
 */
enum class FileStatus {
    PENDING,
    READY,
    FAILED,
    DELETED,
}
