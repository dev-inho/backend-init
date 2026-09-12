package cc.midolog.sample.port.file

/**
 * 파일 저장을 추상화했던 구버전 도메인 출력 포트.
 *
 * 새로운 파일 도메인 표준 포트인 [cc.midolog.file.port.storage.FileStoragePort]로 대체되었다.
 * 하위 호환성을 위해 심볼을 유지하며, 기존 어댑터 구현체와의 호환성을 제공한다.
 */
@Deprecated(
    message = "cc.midolog.file.port.storage.FileStoragePort 로 대체",
    replaceWith = ReplaceWith("cc.midolog.file.port.storage.FileStoragePort"),
)
interface FileStoragePort {
    suspend fun store(path: String, bytes: ByteArray): String
}
