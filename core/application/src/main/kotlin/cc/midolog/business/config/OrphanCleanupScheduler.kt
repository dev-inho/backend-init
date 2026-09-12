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
 * 고아 파일 정리 작업의 실행 주기, 만료 기준 시간, 일괄 처리 크기 등 운영 정책을 정의하는 설정.
 * 스토리지 부하와 DB 부하 사이의 균형을 맞출 수 있도록 조정 가능하다.
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
 * 고아 파일 정리를 시스템 백그라운드에서 주기적으로 트리거하는 스케줄러.
 * 실행 간격보다 작업 시간이 길어질 경우 중복 실행을 막기 위해 AtomicBoolean 락을 사용하며,
 * 비정상 종료 시에도 작업 유실이나 메모리 누수가 없도록 코루틴 생명주기를 스프링 컨텍스트에 맞춰 관리한다.
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
