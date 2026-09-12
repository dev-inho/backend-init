package cc.midolog.business.service

import cc.midolog.file.model.FileStatus
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.FileStoragePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/**
 * PENDING 상태로 방치되어 스토리지 공간만 차지하는 고아 파일을 정기적으로 회수하고 삭제 상태(FAILED)로 전이하는 서비스.
 * 재시도 과정에서 영구 실패한 ID들을 건너뜀으로써 후속 항목이 처리되지 못하는 기아 현상을 회피한다.
 */
@Service
class FileOrphanCleanupService(
    private val clock: Clock,
    private val fileMetaRepositoryPort: FileMetaRepositoryPort,
    private val fileStoragePort: FileStoragePort
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun cleanup(pendingTtl: Duration, batchSize: Int) {
        require(batchSize >= 1) { "batch-size must be at least 1" }

        val cutoff = clock.instant().minus(pendingTtl)
        val failedIds = mutableSetOf<String>()

        while (true) {
            val limit = batchSize + failedIds.size
            val orphans = fileMetaRepositoryPort.findExpiredPending(cutoff, limit)
            if (orphans.isEmpty()) {
                break
            }

            var newItemsProcessed = 0
            for (orphan in orphans) {
                if (failedIds.contains(orphan.id)) {
                    continue
                }
                newItemsProcessed++

                try {
                    val deleted = fileStoragePort.delete(orphan.storageKey)
                    if (deleted) {
                        val updated = fileMetaRepositoryPort.updateStatus(orphan.id, FileStatus.FAILED)
                        if (updated) {
                            log.info("Cleaned up orphan file, id: ${orphan.id}, storageKey: ${orphan.storageKey}")
                        } else {
                            log.warn("Failed to update status to FAILED, id: ${orphan.id}")
                            failedIds.add(orphan.id)
                        }
                    } else {
                        log.warn("Failed to delete orphan storage (returned false), id: ${orphan.id}, storageKey: ${orphan.storageKey}")
                        failedIds.add(orphan.id)
                    }
                } catch (e: Exception) {
                    log.error("Exception during orphan cleanup, id: ${orphan.id}, storageKey: ${orphan.storageKey}", e)
                    failedIds.add(orphan.id)
                }
            }

            if (newItemsProcessed == 0) {
                // 더 이상 처리할 새로운 고아 건이 없음
                break
            }

            // 조회된 항목 수가 limit보다 작으면 더 이상 남은 고아 건이 없음
            if (orphans.size < limit) {
                break
            }
        }
    }
}
