package cc.midolog.storage.file.local

import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.StoredFile
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.FileStoragePort
import cc.midolog.storage.file.autoconfigure.FileStorageProperties
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import kotlin.io.path.absolutePathString
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.isSymbolicLink
import java.util.UUID

@Component
class LocalFileStorageAdapter(
    properties: FileStorageProperties
) : FileStoragePort {

    private val rootPath: Path = Paths.get(properties.local.rootDir!!).toAbsolutePath().normalize()

    init {
        if (!Files.exists(rootPath)) {
            Files.createDirectories(rootPath)
        }
    }

    private fun resolveSafePath(key: String): Path {
        try {
            UUID.fromString(key)
        } catch (e: Exception) {
            throw IllegalArgumentException("Invalid UUID format")
        }
        val target = rootPath.resolve(key).normalize()
        if (!target.startsWith(rootPath)) {
            throw SecurityException("Path traversal attempt")
        }
        if (target.isSymbolicLink()) {
            throw IllegalArgumentException("Symbolic links are not allowed")
        }
        return target
    }

    override suspend fun store(
        key: String,
        reader: ChunkReader,
        knownSize: Long?,
        contentType: String,
        expectedChecksum: String?
    ): StoredFile {
        val targetPath = resolveSafePath(key)
        val tempPath = rootPath.resolve("$key.tmp")
        val buffer = ByteArray(8192)
        val digest = MessageDigest.getInstance("SHA-256")
        var size = 0L

        try {
            Files.newOutputStream(tempPath).use { out ->
                while (true) {
                    val read = reader.readChunk(buffer)
                    if (read == -1) break
                    out.write(buffer, 0, read)
                    digest.update(buffer, 0, read)
                    size += read
                }
            }

            val checksum = digest.digest().joinToString("") { "%02x".format(it) }

            if (expectedChecksum != null && expectedChecksum != checksum) {
                throw IllegalStateException("체크섬이 다릅니다")
            }
            if (knownSize != null && knownSize != size) {
                throw IllegalStateException("크기가 다릅니다")
            }

            Files.move(tempPath, targetPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING)

            return StoredFile(
                id = "",
                ownerId = "",
                storageKey = key,
                sizeBytes = size,
                contentType = contentType,
                checksum = checksum,
                status = FileStatus.READY
            )
        } catch (e: Exception) {
            tempPath.deleteIfExists()
            reader.cancel(e)
            throw e
        } finally {
            reader.close()
        }
    }

    override suspend fun load(key: String): ChunkReader? {
        val targetPath = resolveSafePath(key)
        if (!targetPath.exists()) return null

        val channel = Files.newByteChannel(targetPath, java.nio.file.StandardOpenOption.READ)
        return object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int {
                val byteBuffer = java.nio.ByteBuffer.wrap(buffer)
                val read = channel.read(byteBuffer)
                return if (read > 0) read else -1
            }

            override suspend fun cancel(cause: Throwable?) {
                channel.close()
            }

            override fun close() {
                channel.close()
            }
        }
    }

    override suspend fun delete(key: String): Boolean {
        val targetPath = resolveSafePath(key)
        return targetPath.deleteIfExists()
    }

    override suspend fun exists(key: String): Boolean {
        val targetPath = resolveSafePath(key)
        return targetPath.exists()
    }
}
