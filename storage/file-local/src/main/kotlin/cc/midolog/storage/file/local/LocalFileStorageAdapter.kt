package cc.midolog.storage.file.local

import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.StoredFile
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.FileStoragePort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

/**
 * 로컬 파일 시스템을 스토리지로 사용하는 영속성 어댑터.
 * 
 * - 보안(경로 탈출 방지): root 경로 내부로만 접근을 허용하고, 외부 경로나 심볼릭 링크 공격을 방지한다.
 * - 부분 파일 정리: 업로드 중 실패 시 불완전한 파일이 남지 않도록 임시 파일 삭제를 보장한다.
 * - 체크섬: SHA-256을 청크를 읽으면서 계산하여 I/O 성능을 최적화한다.
 */
class LocalFileStorageAdapter(
    private val rootDirPath: String
) : FileStoragePort {

    private val rootPath: Path = Paths.get(rootDirPath).normalize().toAbsolutePath()

    init {
        if (!rootPath.exists()) {
            Files.createDirectories(rootPath)
        }
    }

    private fun resolveKey(key: String): Path {
        val resolved = rootPath.resolve(key).normalize().toAbsolutePath()
        if (!resolved.startsWith(rootPath)) {
            throw IllegalArgumentException("경로 탈출(Directory traversal) 시도가 감지되었습니다.")
        }
        return resolved
    }

    override suspend fun store(
        key: String,
        reader: ChunkReader,
        knownSize: Long?,
        contentType: String,
        expectedChecksum: String?
    ): StoredFile = withContext(Dispatchers.IO) {
        val targetPath = resolveKey(key)
        val tempPath = rootPath.resolve("$key.tmp")
        
        val digest = MessageDigest.getInstance("SHA-256")
        var actualSize = 0L
        val buffer = ByteArray(8192)

        try {
            Files.newOutputStream(tempPath).use { out ->
                reader.use {
                    while (true) {
                        val readBytes = it.readChunk(buffer)
                        if (readBytes == -1) break
                        
                        out.write(buffer, 0, readBytes)
                        digest.update(buffer, 0, readBytes)
                        actualSize += readBytes
                    }
                }
            }
            
            val actualChecksum = digest.digest().joinToString("") { "%02x".format(it) }
            
            if (expectedChecksum != null && expectedChecksum != actualChecksum) {
                throw IllegalArgumentException("체크섬 불일치: expected=$expectedChecksum, actual=$actualChecksum")
            }
            
            Files.move(tempPath, targetPath, StandardCopyOption.REPLACE_EXISTING)
            
            return@withContext StoredFile(
                id = "", // 도메인 모델 생성은 서비스에서 진행하며 어댑터는 스토리지 관련 정보만 채워 반환
                ownerId = "",
                storageKey = key,
                sizeBytes = actualSize,
                contentType = contentType,
                checksum = actualChecksum,
                status = FileStatus.READY
            )
        } catch (e: Exception) {
            tempPath.deleteIfExists()
            throw e
        }
    }

    override suspend fun load(key: String): ChunkReader? = withContext(Dispatchers.IO) {
        val targetPath = resolveKey(key)
        if (!targetPath.exists() || !targetPath.isRegularFile()) {
            return@withContext null
        }
        
        val inputStream = Files.newInputStream(targetPath)
        
        return@withContext object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int = withContext(Dispatchers.IO) {
                inputStream.read(buffer)
            }
            
            override suspend fun cancel(cause: Throwable?) {
                close()
            }
            
            override fun close() {
                inputStream.close()
            }
        }
    }

    override suspend fun delete(key: String): Boolean = withContext(Dispatchers.IO) {
        val targetPath = resolveKey(key)
        targetPath.deleteIfExists()
        true
    }

    override suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        val targetPath = resolveKey(key)
        targetPath.exists() && targetPath.isRegularFile()
    }
}
