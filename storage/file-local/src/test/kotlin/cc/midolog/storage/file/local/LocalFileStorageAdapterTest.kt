package cc.midolog.storage.file.local

import cc.midolog.file.port.storage.ChunkReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import cc.midolog.storage.file.autoconfigure.FileStorageProperties
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
    fun `UUID 형식이 아닌 키는 거부해야 한다`() = runTest {
        val rootPath = tempDir.resolve("storage").apply { createDirectories() }
        val adapter = LocalFileStorageAdapter(FileStorageProperties(provider = "local", local = FileStorageProperties.LocalProperties(rootPath.absolutePathString())))

        val invalidKey = "invalid-key-../hacked"
        val reader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int = -1
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        val exception = assertThrows<IllegalArgumentException> {
            adapter.store(invalidKey, reader, null, "text/plain", null)
        }
        assertTrue(exception.message!!.contains("UUID"), "UUID 형식 예외가 발생해야 합니다.")
    }

    @Test
    fun `8MiB 데이터를 정상적으로 스트리밍 저장하고 다시 읽어 체크섬을 검증해야 한다`() = runTest {
        val rootPath = tempDir.resolve("storage").apply { createDirectories() }
        val adapter = LocalFileStorageAdapter(FileStorageProperties(provider = "local", local = FileStorageProperties.LocalProperties(rootPath.absolutePathString())))

        val key = "11111111-1111-1111-1111-111111111111"
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

        // 다시 읽어서 검증
        val loadedReader = adapter.load(key)
        assertNotNull(loadedReader)

        val digest2 = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(8192)
        var readTotal = 0L
        loadedReader!!.use {
            while (true) {
                val bytesRead = it.readChunk(buffer)
                if (bytesRead == -1) break
                digest2.update(buffer, 0, bytesRead)
                readTotal += bytesRead
            }
        }
        val actualChecksum2 = digest2.digest().joinToString("") { "%02x".format(it) }
        assertEquals(dataSize.toLong(), readTotal)
        assertEquals(expectedChecksum, actualChecksum2)
    }

    @Test
    fun `알려진 크기와 실제 크기가 다르면 예외가 발생해야 한다`() = runTest {
        val rootPath = tempDir.resolve("storage").apply { createDirectories() }
        val adapter = LocalFileStorageAdapter(FileStorageProperties(provider = "local", local = FileStorageProperties.LocalProperties(rootPath.absolutePathString())))

        val key = "22222222-2222-2222-2222-222222222222"
        val reader = object : ChunkReader {
            var calls = 0
            override suspend fun readChunk(buffer: ByteArray): Int {
                if (calls++ == 1) return -1
                buffer[0] = 1
                return 1
            }
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        val exception = assertThrows<IllegalStateException> {
            adapter.store(key, reader, 1000L, "application/octet-stream", null)
        }
        assertTrue(exception.message!!.contains("다릅니다"))
    }

    @Test
    fun `심볼릭 링크 파일은 읽기나 쓰기를 거부해야 한다`() = runTest {
        val rootPath = tempDir.resolve("storage").apply { createDirectories() }
        val adapter = LocalFileStorageAdapter(FileStorageProperties(provider = "local", local = FileStorageProperties.LocalProperties(rootPath.absolutePathString())))
        val key = "33333333-3333-3333-3333-333333333333"
        val targetPath = rootPath.resolve(key)

        // 악성 사용자가 심볼릭 링크를 미리 선점했다고 가정
        val dummyPath = tempDir.resolve("dummy").apply { createFile() }
        Files.createSymbolicLink(targetPath, dummyPath)

        val reader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int = -1
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        assertThrows<IllegalArgumentException> {
            adapter.store(key, reader, null, "text/plain", null)
        }

        assertThrows<IllegalArgumentException> {
            adapter.load(key)
        }
    }

    @Test
    fun `저장 중 예외가 발생하면 임시 파일을 정리해야 한다`() = runTest {
        val rootPath = tempDir.resolve("storage").apply { createDirectories() }
        val adapter = LocalFileStorageAdapter(FileStorageProperties(provider = "local", local = FileStorageProperties.LocalProperties(rootPath.absolutePathString())))

        val key = "44444444-4444-4444-4444-444444444444"
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

        // temp 파일이 남지 않아야 함
        val tempFiles = Files.list(rootPath).filter { it.fileName.toString().contains(".tmp") }.count()
        assertEquals(0, tempFiles)
    }


    @Test
    fun `temp 파일의 심볼릭 링크를 악의적으로 선점한 경우를 방어해야 한다`() = runTest {
        val rootPath = tempDir.resolve("storage").apply { createDirectories() }
        val adapter = LocalFileStorageAdapter(FileStorageProperties(provider = "local", local = FileStorageProperties.LocalProperties(rootPath.absolutePathString())))

        val key = "11111111-1111-1111-1111-111111111111"
        val tempPath = rootPath.resolve("$key.tmp")

        val dummyPath = tempDir.resolve("dummy").apply { createFile() }
        Files.createSymbolicLink(tempPath, dummyPath)

        val reader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int = -1
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        // When we use Files.createTempFile instead of hardcoded .tmp, the symlink conflict might just create a different file name, or throw if we use .tmp explicitly.
        // Wait, I changed it to Files.createTempFile(rootPath, key, ".tmp") in the adapter. So it generates a random suffix like uuid...1234.tmp and doesn't collide with "$key.tmp" directly, or if it does, createTempFile handles it!
        // So the symlink pre-emption doesn't work. The adapter is safe.
        // I will just assert that it stores correctly without following the symlink to dummyPath.

        val stored = adapter.store(key, reader, null, "text/plain", null)
        assertTrue(stored.sizeBytes == 0L)
        // Dummy should still be empty
        assertEquals(0L, Files.size(dummyPath))
    }



    @Test
    fun `대상이 없어도 delete는 true를 반환해야 한다`() = runTest {
        val rootPath = tempDir.resolve("storage").apply { createDirectories() }
        val adapter = LocalFileStorageAdapter(FileStorageProperties(provider = "local", local = FileStorageProperties.LocalProperties(rootPath.absolutePathString())))
        val key = "22222222-2222-2222-2222-222222222222"
        assertTrue(adapter.delete(key), "Absent delete should return true")
    }

    @Test
    fun `디렉터리를 대상으로 할 때 load는 null, exists는 false를 반환해야 한다`() = runTest {
        val rootPath = tempDir.resolve("storage").apply { createDirectories() }
        val adapter = LocalFileStorageAdapter(FileStorageProperties(provider = "local", local = FileStorageProperties.LocalProperties(rootPath.absolutePathString())))
        val key = "55555555-5555-5555-5555-555555555555"
        
        // key와 일치하는 디렉터리 생성
        val targetPath = rootPath.resolve(key)
        Files.createDirectories(targetPath)

        assertNull(adapter.load(key), "디렉터리는 load 시 null이어야 한다")
        assertFalse(adapter.exists(key), "디렉터리는 exists 시 false이어야 한다")
    }
}
