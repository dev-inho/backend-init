package cc.midolog.business.config

import cc.midolog.business.service.FileOrphanCleanupService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class OrphanCleanupSchedulerTest {

    private val contextRunner = ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OrphanCleanupScheduler::class.java))
        .withUserConfiguration(MockServiceConfig::class.java)

    @Configuration
    class MockServiceConfig {
        @Bean
        fun fileOrphanCleanupService(): FileOrphanCleanupService {
            return org.mockito.Mockito.mock(FileOrphanCleanupService::class.java)
        }
    }

    @Test
    fun `enabled가 false이거나 누락되면 스케줄러 빈이 생성되지 않는다`() {
        contextRunner.run { context ->
            assertTrue(context.getBeansOfType(OrphanCleanupScheduler::class.java).isEmpty())
        }

        contextRunner.withPropertyValues("storage.file.orphan-cleanup.enabled=false").run { context ->
            assertTrue(context.getBeansOfType(OrphanCleanupScheduler::class.java).isEmpty())
        }
    }

    @Test
    fun `enabled가 true일 때만 스케줄러 빈이 생성된다`() {
        contextRunner.withPropertyValues("storage.file.orphan-cleanup.enabled=true").run { context ->
            assertTrue(context.getBeansOfType(OrphanCleanupScheduler::class.java).isNotEmpty())
        }
    }
}
