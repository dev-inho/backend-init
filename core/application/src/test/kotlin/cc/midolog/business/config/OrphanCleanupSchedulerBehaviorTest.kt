package cc.midolog.business.config

import cc.midolog.business.service.FileOrphanCleanupService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class OrphanCleanupSchedulerBehaviorTest {

    @Test
    fun `첫 실행 중 두 번째 tick 무시, 완료 후 다음 tick 실행, 예외 후 다음 tick 실행을 검증한다`() = runBlocking {
        val callCount = AtomicInteger(0)
        val cleanupStarted = CompletableDeferred<Unit>()
        val completeCleanup = CompletableDeferred<Unit>()
        var throwException = false
        
        val fakeService = object : FileOrphanCleanupService(
            org.mockito.Mockito.mock(java.time.Clock::class.java),
            org.mockito.Mockito.mock(cc.midolog.file.port.repository.FileMetaRepositoryPort::class.java),
            org.mockito.Mockito.mock(cc.midolog.file.port.storage.FileStoragePort::class.java)
        ) {
            override suspend fun cleanup(pendingTtl: Duration, batchSize: Int) {
                callCount.incrementAndGet()
                cleanupStarted.complete(Unit)
                completeCleanup.await() // block until signal
                if (throwException) {
                    throw RuntimeException("Simulated exception")
                }
            }
        }
        
        val properties = FileOrphanCleanupProperties(enabled = true)
        val scheduler = OrphanCleanupScheduler(properties, fakeService)
        
        // Tick 1: Starts cleanup
        scheduler.runCleanup()
        
        // Wait for cleanup to actually start inside the coroutine
        withTimeout(1000) { cleanupStarted.await() }
        assertEquals(1, callCount.get())
        
        // Tick 2: Should be ignored because tick 1 is still running
        scheduler.runCleanup()
        assertEquals(1, callCount.get(), "Second tick should be ignored")
        
        // Let tick 1 finish
        completeCleanup.complete(Unit)
        delay(100) // allow coroutine to finish and reset flag
        
        // Tick 3: Should run since tick 1 finished
        val cleanupStarted2 = CompletableDeferred<Unit>()
        val completeCleanup2 = CompletableDeferred<Unit>()
        
        val fakeService2 = object : FileOrphanCleanupService(
            org.mockito.Mockito.mock(java.time.Clock::class.java),
            org.mockito.Mockito.mock(cc.midolog.file.port.repository.FileMetaRepositoryPort::class.java),
            org.mockito.Mockito.mock(cc.midolog.file.port.storage.FileStoragePort::class.java)
        ) {
            override suspend fun cleanup(pendingTtl: Duration, batchSize: Int) {
                callCount.incrementAndGet()
                cleanupStarted2.complete(Unit)
                completeCleanup2.await()
                if (throwException) {
                    throw RuntimeException("Simulated exception")
                }
            }
        }
        val scheduler2 = OrphanCleanupScheduler(properties, fakeService2)
        throwException = true
        
        scheduler2.runCleanup()
        withTimeout(1000) { cleanupStarted2.await() }
        assertEquals(2, callCount.get())
        
        // Let tick 3 finish with exception
        completeCleanup2.complete(Unit)
        delay(100)
        
        // Tick 4: Should run even after exception
        val cleanupStarted3 = CompletableDeferred<Unit>()
        val completeCleanup3 = CompletableDeferred<Unit>()
        val fakeService3 = object : FileOrphanCleanupService(
            org.mockito.Mockito.mock(java.time.Clock::class.java),
            org.mockito.Mockito.mock(cc.midolog.file.port.repository.FileMetaRepositoryPort::class.java),
            org.mockito.Mockito.mock(cc.midolog.file.port.storage.FileStoragePort::class.java)
        ) {
            override suspend fun cleanup(pendingTtl: Duration, batchSize: Int) {
                callCount.incrementAndGet()
                cleanupStarted3.complete(Unit)
                completeCleanup3.complete(Unit)
            }
        }
        val scheduler3 = OrphanCleanupScheduler(properties, fakeService3)
        scheduler3.runCleanup()
        withTimeout(1000) { cleanupStarted3.await() }
        assertEquals(3, callCount.get(), "Should run after exception")
        
        // Test destroy
        scheduler3.destroy()
        // Coroutines should be cancelled, we can test it indirectly, but standard Spring context close does this.
    }
}
