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

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineName

/**
 * 고아 파일 정리 스케줄러의 동작 방식을 정의하는 설정 속성 클래스.
 */
@ConfigurationProperties(prefix = "storage.file.orphan-cleanup")
@Validated
data class FileOrphanCleanupProperties(
    val enabled: Boolean = false,
    val interval: Duration = Duration.ofMinutes(10),
    val pendingTtl: Duration = Duration.ofHours(1),
    @field:Min(1)
    val batchSize: Int = 100
)

/**
 * 주기적으로 고아 파일을 정리하는 스프링 스케줄러.
 * `storage.file.orphan-cleanup.enabled=true`일 때만 빈으로 등록된다.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(FileOrphanCleanupProperties::class)
@ConditionalOnProperty(prefix = "storage.file.orphan-cleanup", name = ["enabled"], havingValue = "true")
class OrphanCleanupScheduler(
    private val properties: FileOrphanCleanupProperties,
    private val fileOrphanCleanupService: FileOrphanCleanupService
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob() + CoroutineName("OrphanCleanupScope"))
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
