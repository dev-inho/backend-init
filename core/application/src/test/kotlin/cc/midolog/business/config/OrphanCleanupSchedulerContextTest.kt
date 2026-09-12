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
            assertTrue(context.containsBean("clock"))
            assertTrue(context.containsBean("fileOrphanCleanupService"))
        }

        contextRunner.withPropertyValues("storage.file.orphan-cleanup.enabled=false").run { context ->
            assertTrue(context.getBeansOfType(OrphanCleanupScheduler::class.java).isEmpty())
            assertTrue(context.containsBean("fileOrphanCleanupService"))
        }
    }

    @Test
    fun `enabled가 true일 때 스케줄러 빈이 생성된다`() {
        contextRunner.withPropertyValues("storage.file.orphan-cleanup.enabled=true").run { context ->
            assertTrue(context.getBeansOfType(OrphanCleanupScheduler::class.java).isNotEmpty())
            assertTrue(context.containsBean("fileOrphanCleanupService"))
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
