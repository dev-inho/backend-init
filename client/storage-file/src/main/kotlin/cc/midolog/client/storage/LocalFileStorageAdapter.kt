package cc.midolog.client.storage

import cc.midolog.sample.port.file.FileStoragePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths

/**
 * 구버전 [FileStoragePort]를 구현한 로컬 파일 시스템 저장 어댑터.
 *
 * 새로운 파일 도메인 표준 포트인 [cc.midolog.file.port.storage.FileStoragePort] 및 신규 어댑터로 대체되었다.
 * 하위 호환성을 유지하기 위해 컴포넌트를 보존한다.
 * 파일 시스템 I/O 작업의 스레드 블로킹을 방지하기 위해 [Dispatchers.IO] 코루틴 컨텍스트에서 실행한다.
 */
@Deprecated(
    message = "cc.midolog.file.port.storage.FileStoragePort 로 대체",
    replaceWith = ReplaceWith("cc.midolog.file.port.storage.FileStoragePort"),
)
@Component
class LocalFileStorageAdapter : FileStoragePort {
    /**
     * 전달된 경로에 바이트 데이터를 파일로 저장하고 저장된 파일의 절대 경로를 반환한다.
     *
     * 상위 디렉터리가 존재하지 않으면 자동으로 생성하며, 대상 경로에 동일한 파일이 이미 존재하는 경우 [Files.write]의 기본 표준 옵션에 따라 기존 파일 내용을 덮어쓴다(overwrite).
     */
    override suspend fun store(path: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val target = Paths.get(path)
        target.parent?.let { Files.createDirectories(it) }
        Files.write(target, bytes)
        target.toAbsolutePath().toString()
    }
}
