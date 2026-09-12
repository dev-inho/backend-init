package cc.midolog.business.service

import cc.midolog.file.model.FileStatus
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.FileStoragePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

/**
 * 고아(PENDING 상태로 방치된) 파일 메타데이터 및 스토리지를 주기적으로 정리하는 서비스.
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
        var totalProcessedCount = 0
        val maxItemsToProcess = 1000

        while (totalProcessedCount < maxItemsToProcess) {
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
                totalProcessedCount++

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
