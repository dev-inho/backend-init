package cc.midolog.business.config

import cc.midolog.business.service.FileOrphanCleanupService
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.storage.FileStoragePort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
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
        var cleanupCancelled = CompletableDeferred<Unit>()
        var throwException = false

        val fakeService = object : FileOrphanCleanupService(
            org.mockito.Mockito.mock(Clock::class.java),
            org.mockito.Mockito.mock(FileMetaRepositoryPort::class.java),
            org.mockito.Mockito.mock(FileStoragePort::class.java)
        ) {
            override suspend fun cleanup(pendingTtl: Duration, batchSize: Int) {
                callCount.incrementAndGet()
                cleanupStarted.complete(Unit)
                try {
                    completeCleanup.await() // block until test signals
                } catch (e: CancellationException) {
                    cleanupCancelled.complete(Unit)
                    throw e
                }
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

        // 백그라운드에서 스케줄러 재시도를 반복하여 finally(isRunning=false) 직후 호출되도록 함
        val retryJob1 = launch {
            while (!cleanupStarted.isCompleted) {
                scheduler.runCleanup()
                yield()
            }
        }
        oldComplete.complete(Unit) // 첫 번째 틱 완료 신호
        withTimeout(1000) { retryJob1.join() } // 두 번째 틱이 시작됨을 보장
        assertEquals(2, callCount.get(), "Should run again after first finishes")

        // (c) 그 호출이 예외 완료한 신호 뒤 같은 scheduler 재호출
        throwException = true
        cleanupStarted = CompletableDeferred()
        val oldComplete2 = completeCleanup
        completeCleanup = CompletableDeferred()

        val retryJob2 = launch {
            while (!cleanupStarted.isCompleted) {
                scheduler.runCleanup()
                yield()
            }
        }
        oldComplete2.complete(Unit) // 두 번째 틱을 예외로 완료시킴
        withTimeout(1000) { retryJob2.join() }
        assertEquals(3, callCount.get(), "Should run again after second throws exception")

        // (d) destroy 뒤 새 cleanup이 시작되지 않거나 진행 중 job이 취소됨
        scheduler.destroy()

        // destroy()는 scope.cancel()을 호출하므로 현재 진행 중인 세 번째 틱에 CancellationException이 발생함
        withTimeout(1000) { cleanupCancelled.await() }

        // 이제 취소된 상태에서 새 틱이 시작되지 않음을 음성 단언(timeout)
        cleanupStarted = CompletableDeferred()
        scheduler.runCleanup()

        assertThrows(TimeoutCancellationException::class.java) {
            runBlocking {
                withTimeout(200) { cleanupStarted.await() }
            }
        }

        assertEquals(3, callCount.get(), "Should not run after destroy")
    }
}
