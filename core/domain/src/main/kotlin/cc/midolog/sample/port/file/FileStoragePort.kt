package cc.midolog.sample.port.file

/**
 * 파일 저장을 추상화하는 도메인 출력 포트.
 *
 * 어댑터 구현체(LocalFileStorageAdapter)는 지정된 경로의 상위 디렉터리가 없으면
 * 자동으로 생성하고, 파일이 이미 존재하면 내용을 덮어쓴다(overwrite). 저장이 완료되면
 * 저장된 파일의 절대 경로 문자열을 반환하며 블로킹 파일 I/O는 Dispatchers.IO에서 실행한다.
 *
 * 현재 비즈니스 로직과 테스트에서 호출이 없어 docs/DEAD_CODE_CANDIDATES.md #1에 삭제 제안 후보로 등재되어 있다.
 */
interface FileStoragePort {
    suspend fun store(path: String, bytes: ByteArray): String
}
