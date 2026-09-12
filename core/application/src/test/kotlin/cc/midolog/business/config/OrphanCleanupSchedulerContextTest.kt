package cc.midolog.business.config

import cc.midolog.business.service.FileOrphanCleanupService
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.FileStoragePort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class OrphanCleanupSchedulerContextTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                OrphanCleanupScheduler::class.java,
                ClockConfig::class.java
            )
        )
        .withUserConfiguration(FileOrphanCleanupService::class.java, MockDependenciesConfig::class.java)

    @Configuration
    class MockDependenciesConfig {
        @Bean
        fun fileMetaRepositoryPort(): FileMetaRepositoryPort {
            return org.mockito.Mockito.mock(FileMetaRepositoryPort::class.java)
        }

        @Bean
        fun fileStoragePort(): FileStoragePort {
            return org.mockito.Mockito.mock(FileStoragePort::class.java)
        }
    }

    @Configuration
    class FixedClockConfig {
        @Bean
        fun clock(): Clock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneId.of("UTC"))
    }

    @Test
    fun `enabled가 false이거나 누락되어도 FileOrphanCleanupService와 Clock 빈은 생성되며 스케줄러 빈만 없다`() {
        contextRunner.run { context ->
            assertTrue(context.getBeansOfType(OrphanCleanupScheduler::class.java).isEmpty())
            assertNotNull(context.getBean(Clock::class.java))
            assertNotNull(context.getBean(FileOrphanCleanupService::class.java))
        }

        contextRunner.withPropertyValues("storage.file.orphan-cleanup.enabled=false").run { context ->
            assertTrue(context.getBeansOfType(OrphanCleanupScheduler::class.java).isEmpty())
            assertNotNull(context.getBean(FileOrphanCleanupService::class.java))
        }
    }

    @Test
    fun `enabled가 true일 때 스케줄러 빈이 생성되고 프로퍼티 기본값이 유지된다`() {
        contextRunner.withPropertyValues("storage.file.orphan-cleanup.enabled=true").run { context ->
            assertTrue(context.getBeansOfType(OrphanCleanupScheduler::class.java).isNotEmpty())
            assertNotNull(context.getBean(FileOrphanCleanupService::class.java))

            val properties = context.getBean(FileOrphanCleanupProperties::class.java)
            assertEquals(Duration.ofMinutes(10), properties.interval)
            assertEquals(Duration.ofHours(1), properties.pendingTtl)
            assertEquals(100, properties.batchSize)
        }
    }

    @Test
    fun `batchSize가 0이면 컨텍스트 로드에 실패한다 (fail-fast)`() {
        contextRunner.withPropertyValues(
            "storage.file.orphan-cleanup.enabled=true",
            "storage.file.orphan-cleanup.batch-size=0"
        ).run { context ->
            assertNotNull(context.startupFailure, "Context should fail-fast due to batch-size validation")
        }
    }

    @Test
    fun `ClockConfig는 사용자가 제공한 Clock 빈을 대체하지 않는다`() {
        contextRunner.withUserConfiguration(FixedClockConfig::class.java).run { context ->
            val clock = context.getBean(Clock::class.java)
            assertEquals(Instant.parse("2020-01-01T00:00:00Z"), clock.instant())
        }
    }
}
