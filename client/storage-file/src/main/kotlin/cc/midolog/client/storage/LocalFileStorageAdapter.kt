package cc.midolog.client.storage

import cc.midolog.sample.port.file.FileStoragePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Paths

/** 로컬 파일 저장 어댑터 — 도메인의 FileStoragePort 구현. 블로킹 I/O를 Dispatchers.IO로 분리. */
@Component
class LocalFileStorageAdapter : FileStoragePort {
    override suspend fun store(path: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val target = Paths.get(path)
        target.parent?.let { Files.createDirectories(it) }
        Files.write(target, bytes)
        target.toAbsolutePath().toString()
    }
}
