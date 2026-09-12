package cc.midolog.business.service

import cc.midolog.file.model.FileStatus
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.FileStoragePort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Duration

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
            val orphans = fileMetaRepositoryPort.findExpiredPending(cutoff, batchSize)
            if (orphans.isEmpty()) {
                break
            }

            var hasNewItems = false
            for (orphan in orphans) {
                if (failedIds.contains(orphan.id)) {
                    continue
                }
                hasNewItems = true
                
                try {
                    val deleted = fileStoragePort.delete(orphan.storageKey)
                    if (deleted) {
                        fileMetaRepositoryPort.updateStatus(orphan.id, FileStatus.FAILED)
                        log.info("Cleaned up orphan file, id: ${orphan.id}, storageKey: ${orphan.storageKey}")
                    } else {
                        log.warn("Failed to delete orphan storage, id: ${orphan.id}, storageKey: ${orphan.storageKey}")
                        failedIds.add(orphan.id)
                    }
                } catch (e: Exception) {
                    log.error("Exception during orphan cleanup, id: ${orphan.id}, storageKey: ${orphan.storageKey}", e)
                    failedIds.add(orphan.id)
                }
            }

            if (!hasNewItems) {
                log.warn("Only previously failed items were returned. Stopping to prevent infinite loop.")
                break
            }

            if (orphans.size < batchSize) {
                break
            }
        }
    }
}
