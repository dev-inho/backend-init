package cc.midolog.business.service

import cc.midolog.file.model.FileMeta
import cc.midolog.file.model.FileStatus
import cc.midolog.file.model.StoredFile
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.ChunkReader
import cc.midolog.file.port.storage.FileStoragePort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class FileUploadInterruptionTest {

    /**
     * [테스트 대체 설명]
     * 프로세스 비정상 중단(kill -9 등)을 테스트에서 직접 재현할 수 없으므로,
     * PENDING 상태 저장 직후 (스토리지 저장 및 완료 전) 발생하는 중단을 Fake Exception (UploadInterruptedException)으로 시뮬레이션한다.
     * 단, `uploadFile`이 내부에서 Exception을 잡아 FAILED로 전이시키는 정상 계약을 우회하기 위해,
     * 일반적인 RuntimeException이나 Exception이 아닌 Error(VirtualMachineError 등 심각한 수준)를 던져서
     * catch(Exception) 블록을 회피하고 PENDING 상태가 남도록 재현한다. (여기서는 OutOfMemoryError로 테스트)
     */
    @Test
    fun `스트림 처리 도중 비정상 중단(OOM 등)으로 PENDING 상태가 남은 객체는 TTL 후 고아 정리가 FAILED로 회수한다`() = runBlocking {
        val now = Instant.parse("2026-09-12T10:00:00Z")
        val clock = Clock.fixed(now, ZoneId.of("UTC"))
        
        val db = mutableMapOf<String, FileMeta>()
        
        val storagePort = object : FileStoragePort {
            override suspend fun store(key: String, reader: ChunkReader, knownSize: Long?, contentType: String, expectedChecksum: String?): StoredFile {
                // 비정상 중단 시뮬레이션: 일반 Exception을 catch하는 로직을 회피하기 위해 Error를 발생
                throw OutOfMemoryError("Simulated abrupt JVM crash after PENDING is saved")
            }
            override suspend fun load(key: String): ChunkReader? = null
            override suspend fun delete(key: String): Boolean = true
            override suspend fun exists(key: String): Boolean = false
        }

        val repoPort = object : FileMetaRepositoryPort {
            override suspend fun findById(id: String): FileMeta? = db[id]
            override suspend fun save(file: FileMeta): FileMeta {
                // PENDING 저장 시뮬레이션 시간은 고정 시간 - 2시간으로 과거 생성된 것으로 처리
                val pastTime = now.minus(Duration.ofHours(2))
                val saved = file.copy(createdAt = pastTime, updatedAt = pastTime)
                db[saved.id] = saved
                return saved
            }
            override suspend fun updateStatus(id: String, status: FileStatus): Boolean {
                val f = db[id]
                if (f != null) {
                    db[id] = f.copy(status = status, updatedAt = now)
                    return true
                }
                return false
            }
            override suspend fun findExpiredPending(cutoffTime: Instant, limit: Int): List<FileMeta> {
                return db.values.filter { it.status == FileStatus.PENDING && it.updatedAt.isBefore(cutoffTime) }
                    .sortedBy { it.updatedAt }
                    .take(limit)
            }
        }

        val fileService = FileService(storagePort, repoPort)
        val cleanupService = FileOrphanCleanupService(clock, repoPort, storagePort)

        val reader = object : ChunkReader {
            override suspend fun readChunk(buffer: ByteArray): Int = -1
            override suspend fun cancel(cause: Throwable?) {}
            override fun close() {}
        }

        // 1. 비정상 중단 발생
        assertThrows(OutOfMemoryError::class.java) {
            runBlocking {
                fileService.uploadFile("owner1", "text/plain", reader)
            }
        }

        // 2. PENDING 메타가 FAILED로 즉시 전이되지 않고 DB에 남았는지 확인
        assertEquals(1, db.size)
        val interruptedFileId = db.keys.first()
        assertEquals(FileStatus.PENDING, db[interruptedFileId]?.status, "Should remain PENDING due to JVM crash simulation")

        // 3. 고아 정리 실행 (TTL 1시간)
        cleanupService.cleanup(Duration.ofHours(1), 100)

        // 4. 고아 정리에 의해 FAILED로 회수되었는지 검증
        assertEquals(FileStatus.FAILED, db[interruptedFileId]?.status, "Should be recovered and marked as FAILED by orphan cleanup")
    }
}
