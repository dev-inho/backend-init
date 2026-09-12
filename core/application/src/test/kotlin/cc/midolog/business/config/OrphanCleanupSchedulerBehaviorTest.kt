package cc.midolog.business.config

import cc.midolog.business.service.FileOrphanCleanupService
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.FileStoragePort
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

class OrphanCleanupSchedulerBehaviorTest {

    @Test
    fun `한 스케줄러 인스턴스에서 tick 중복 무시 및 완료 예외 후 정상 재개를 단언한다`() = runBlocking {
        val properties = FileOrphanCleanupProperties(enabled = true)
        val callCount = AtomicInteger(0)
        
        var cleanupStarted = CompletableDeferred<Unit>()
        var completeCleanup = CompletableDeferred<Unit>()
        var throwException = false
        
        val fakeService = object : FileOrphanCleanupService(
            org.mockito.Mockito.mock(Clock::class.java),
            org.mockito.Mockito.mock(FileMetaRepositoryPort::class.java),
            org.mockito.Mockito.mock(FileStoragePort::class.java)
        ) {
            override suspend fun cleanup(pendingTtl: Duration, batchSize: Int) {
                callCount.incrementAndGet()
                cleanupStarted.complete(Unit)
                completeCleanup.await() // block until test signals
                if (throwException) {
                    throw RuntimeException("Simulated exception")
                }
            }
        }
        
        val scheduler = OrphanCleanupScheduler(properties, fakeService)
        
        // (a) 첫 호출 진행 중 재호출 무시
        scheduler.runCleanup()
        withTimeout(1000) { cleanupStarted.await() }
        assertEquals(1, callCount.get())
        
        scheduler.runCleanup() // Should be ignored because tick 1 is blocked at completeCleanup.await()
        assertEquals(1, callCount.get(), "Second tick should be ignored while first is running")
        
        // (b) 첫 호출 정상 완료 신호 뒤 같은 scheduler 재호출
        cleanupStarted = CompletableDeferred()
        val oldComplete = completeCleanup
        completeCleanup = CompletableDeferred()
        oldComplete.complete(Unit) // Finish tick 1
        
        // Wait for coroutine to actually finish and reset isRunning flag
        withTimeout(1000) {
            while(true) {
                // Trying to run again, if it's still running it will return immediately without side effects,
                // but we want to know when it *does* launch the second one.
                scheduler.runCleanup()
                if (cleanupStarted.isCompleted) {
                    break
                }
                delay(10)
            }
        }
        assertEquals(2, callCount.get(), "Should run again after first finishes")
        
        // (c) 그 호출이 예외 완료한 신호 뒤 같은 scheduler 재호출
        throwException = true
        cleanupStarted = CompletableDeferred()
        val oldComplete2 = completeCleanup
        completeCleanup = CompletableDeferred()
        oldComplete2.complete(Unit) // Finish tick 2 with exception
        
        withTimeout(1000) {
            while(true) {
                scheduler.runCleanup()
                if (cleanupStarted.isCompleted) {
                    break
                }
                delay(10)
            }
        }
        assertEquals(3, callCount.get(), "Should run again after second throws exception")
        
        // (d) destroy 뒤 새 cleanup이 시작되지 않거나 진행 중 job이 취소됨
        scheduler.destroy()
        
        cleanupStarted = CompletableDeferred()
        val oldComplete3 = completeCleanup
        completeCleanup = CompletableDeferred()
        oldComplete3.complete(Unit) // Release tick 3
        
        delay(100)
        scheduler.runCleanup() // Should not run because scope is cancelled
        
        // It should either not start or fail to launch. 
        // If it was cancelled, trying to launch in a cancelled scope is silently ignored or throws depending on Job.
        // We can just verify callCount hasn't increased from our latest manual runCleanup.
        delay(50)
        assertEquals(3, callCount.get(), "Should not run after destroy")
    }
}
