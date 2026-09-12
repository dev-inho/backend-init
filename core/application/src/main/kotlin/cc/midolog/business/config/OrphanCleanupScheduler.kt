package cc.midolog.business.config

import cc.midolog.business.service.FileOrphanCleanupService
import jakarta.annotation.PreDestroy
import jakarta.validation.constraints.Min
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.validation.annotation.Validated
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean

@ConfigurationProperties(prefix = "storage.file.orphan-cleanup")
@Validated
data class FileOrphanCleanupProperties(
    val enabled: Boolean = false,
    val interval: Duration = Duration.ofMinutes(10),
    val pendingTtl: Duration = Duration.ofHours(1),
    @field:Min(1)
    val batchSize: Int = 100
)

@Configuration
@EnableScheduling
@EnableConfigurationProperties(FileOrphanCleanupProperties::class)
@ConditionalOnProperty(prefix = "storage.file.orphan-cleanup", name = ["enabled"], havingValue = "true")
class OrphanCleanupScheduler(
    private val properties: FileOrphanCleanupProperties,
    private val fileOrphanCleanupService: FileOrphanCleanupService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val isRunning = AtomicBoolean(false)

    @Scheduled(fixedDelayString = "\${storage.file.orphan-cleanup.interval:10m}")
    fun runCleanup() {
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Orphan cleanup is already running, skipping this iteration.")
            return
        }
        scope.launch {
            try {
                fileOrphanCleanupService.cleanup(properties.pendingTtl, properties.batchSize)
            } catch (e: Exception) {
                log.error("Failed to run orphan cleanup", e)
            } finally {
                isRunning.set(false)
            }
        }
    }

    @PreDestroy
    fun destroy() {
        scope.cancel()
    }
}
