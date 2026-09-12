package cc.midolog.storage.file.local

import cc.midolog.file.port.storage.ChunkReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.assertThrows
import kotlin.io.path.absolutePathString

class LocalFileStorageAdapterTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `root 디렉토리를 벗어나는 경로 순회 공격을 차단해야 한다`() = runTest {
        val rootPath = tempDir.resolve("storage").apply { createDirectories() }
        val adapter = LocalFileStorageAdapter(rootPath.absolutePathString())

        val traversalKey = "../hacked.txt"
        val reader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int = -1
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        val exception = assertThrows<IllegalArgumentException> {
            adapter.store(traversalKey, reader, null, "text/plain", null)
        }
        assertTrue(exception.message!!.contains("탈출"), "경로 순회 방어 예외가 발생해야 합니다. 메시지: ${exception.message}")
    }

    @Test
    fun `8MiB 데이터를 정상적으로 스트리밍 저장하고 체크섬을 검증해야 한다`() = runTest {
        val rootPath = tempDir.resolve("storage").apply { createDirectories() }
        val adapter = LocalFileStorageAdapter(rootPath.absolutePathString())
        
        val key = "uuid-test-key-1"
        val dataSize = 8 * 1024 * 1024 // 8MiB
        val randomData = ByteArray(dataSize)
        (0 until dataSize).forEach { randomData[it] = (it % 256).toByte() }
        
        val expectedDigest = MessageDigest.getInstance("SHA-256")
        expectedDigest.update(randomData)
        val expectedChecksum = expectedDigest.digest().joinToString("") { "%02x".format(it) }

        var offset = 0
        val reader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int {
                if (offset >= dataSize) return -1
                val length = minOf(buffer.size, dataSize - offset)
                System.arraycopy(randomData, offset, buffer, 0, length)
                offset += length
                return length
            }
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        val storedFile = adapter.store(key, reader, dataSize.toLong(), "application/octet-stream", expectedChecksum)
        
        assertEquals(key, storedFile.storageKey)
        assertEquals(dataSize.toLong(), storedFile.sizeBytes)
        assertEquals(expectedChecksum, storedFile.checksum)
        assertTrue(rootPath.resolve(key).exists())
        
        val loadedReader = adapter.load(key)
        assertNotNull(loadedReader)
        loadedReader!!.close()
    }

    @Test
    fun `저장 중 예외가 발생하면 임시 파일을 정리해야 한다`() = runTest {
        val rootPath = tempDir.resolve("storage").apply { createDirectories() }
        val adapter = LocalFileStorageAdapter(rootPath.absolutePathString())
        
        val key = "uuid-test-key-error"
        val reader = object : ChunkReader {
            var calls = 0
            override suspend fun readChunk(buffer: ByteArray): Int {
                if (calls++ == 1) throw RuntimeException("Simulated error")
                buffer[0] = 1
                return 1
            }
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        assertThrows<RuntimeException> {
            adapter.store(key, reader, null, "application/octet-stream", null)
        }
        
        assertFalse(rootPath.resolve(key).exists())
    }
}
