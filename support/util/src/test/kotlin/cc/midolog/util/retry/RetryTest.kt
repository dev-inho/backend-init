package cc.midolog.util.retry

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RetryTest {

    @Test
    fun `첫 시도 성공 시 재시도 없이 결과를 반환한다`() {
        val result = retry(
            RetryPolicy(maxAttempts = 3, initialDelayMillis = 100),
            block = { "success" }
        )
        assertEquals("success", result)
    }

    @Test
    fun `2번 실패 후 성공 시 결과를 반환한다`() {
        var attemptCount = 0
        val result = retry(
            RetryPolicy(maxAttempts = 3, initialDelayMillis = 10),
            sleeper = { /* 지연 무시 */ },
            block = {
                attemptCount++
                if (attemptCount < 3) {
                    throw IllegalStateException("attempt $attemptCount failed")
                }
                "success"
            }
        )
        assertEquals("success", result)
        assertEquals(3, attemptCount)
    }

    @Test
    fun `모든 시도가 실패하면 마지막 예외를 던진다`() {
        val exception = assertThrows(IllegalStateException::class.java) {
            retry(
                RetryPolicy(maxAttempts = 3, initialDelayMillis = 10),
                sleeper = { /* 지연 무시 */ },
                block = { throw IllegalStateException("persistent failure") }
            )
        }
        assertEquals("persistent failure", exception.message)
    }

    @Test
    fun `retryOn이 false이면 첫 예외를 즉시 던진다`() {
        var attemptCount = 0
        val exception = assertThrows(IllegalStateException::class.java) {
            retry(
                RetryPolicy(
                    maxAttempts = 3,
                    initialDelayMillis = 10,
                    retryOn = { false }  // 재시도 안 함
                ),
                sleeper = { /* 지연 무시 */ },
                block = {
                    attemptCount++
                    throw IllegalStateException("not retryable")
                }
            )
        }
        assertEquals("not retryable", exception.message)
        assertEquals(1, attemptCount)  // 1회만 시도
    }

    @Test
    fun `특정 예외 타입만 재시도한다`() {
        var attemptCount = 0
        val exception = assertThrows(IllegalArgumentException::class.java) {
            retry(
                RetryPolicy(
                    maxAttempts = 3,
                    initialDelayMillis = 10,
                    retryOn = { it is IllegalStateException }  // IllegalStateException만 재시도
                ),
                sleeper = { /* 지연 무시 */ },
                block = {
                    attemptCount++
                    if (attemptCount == 1) {
                        throw IllegalStateException("retryable exception")
                    } else {
                        throw IllegalArgumentException("not retryable")
                    }
                }
            )
        }
        assertEquals("not retryable", exception.message)
        assertEquals(2, attemptCount)  // 첫 시도는 재시도, 두 번째는 다른 예외로 중단
    }

    @Test
    fun `delayForAttempt 0번째 시도는 initialDelayMillis를 반환한다`() {
        val policy = RetryPolicy(maxAttempts = 3, initialDelayMillis = 100)
        assertEquals(100, policy.delayForAttempt(0))
    }

    @Test
    fun `delayForAttempt는 지수 백오프를 적용한다`() {
        val policy = RetryPolicy(
            maxAttempts = 5,
            initialDelayMillis = 10,
            multiplier = 2.0
        )
        assertEquals(10, policy.delayForAttempt(0))    // 10 * 2^0 = 10
        assertEquals(20, policy.delayForAttempt(1))    // 10 * 2^1 = 20
        assertEquals(40, policy.delayForAttempt(2))    // 10 * 2^2 = 40
        assertEquals(80, policy.delayForAttempt(3))    // 10 * 2^3 = 80
        assertEquals(160, policy.delayForAttempt(4))   // 10 * 2^4 = 160
    }

    @Test
    fun `delayForAttempt는 maxDelayMillis를 상한으로 적용한다`() {
        val policy = RetryPolicy(
            maxAttempts = 5,
            initialDelayMillis = 10,
            multiplier = 2.0,
            maxDelayMillis = 50
        )
        assertEquals(10, policy.delayForAttempt(0))   // 10
        assertEquals(20, policy.delayForAttempt(1))   // 20
        assertEquals(40, policy.delayForAttempt(2))   // 40
        assertEquals(50, policy.delayForAttempt(3))   // min(80, 50) = 50
        assertEquals(50, policy.delayForAttempt(4))   // min(160, 50) = 50
    }

    @Test
    fun `초기 지연이 0일 때 delayForAttempt는 항상 0을 반환한다`() {
        val policy = RetryPolicy(
            maxAttempts = 5,
            initialDelayMillis = 0,
            multiplier = 2.0
        )
        assertEquals(0, policy.delayForAttempt(0))
        assertEquals(0, policy.delayForAttempt(1))
        assertEquals(0, policy.delayForAttempt(2))
    }

    @Test
    fun `sleeper가 기록하는 지연 시퀀스를 검증한다`() {
        val delays = mutableListOf<Long>()
        retry(
            RetryPolicy(maxAttempts = 4, initialDelayMillis = 10, multiplier = 2.0),
            sleeper = { delays.add(it) },
            block = {
                if (delays.size < 3) {  // 처음 3회는 실패
                    throw RuntimeException("fail")
                }
                "success"
            }
        )
        assertEquals(listOf(10L, 20L, 40L), delays)  // 3번의 재시도 지연
    }

    @Test
    fun `sleeper 주입으로 실제 Thread sleep을 피한다`() {
        val startTime = System.currentTimeMillis()
        val delays = mutableListOf<Long>()
        retry(
            RetryPolicy(maxAttempts = 3, initialDelayMillis = 1000, multiplier = 2.0),
            sleeper = { delays.add(it) },  // 실제 sleep 대신 기록
            block = {
                if (delays.size < 2) {
                    throw RuntimeException("fail")
                }
                "success"
            }
        )
        val elapsed = System.currentTimeMillis() - startTime
        assertEquals(listOf(1000L, 2000L), delays)
        // 실제 sleep 없이 빠르게 완료되어야 함 (100ms 이내)
        assert(elapsed < 100)
    }

    @Test
    fun `maxAttempts가 1이면 재시도 없이 1회만 시도한다`() {
        var attemptCount = 0
        assertThrows(RuntimeException::class.java) {
            retry(
                RetryPolicy(maxAttempts = 1, initialDelayMillis = 10),
                sleeper = { /* 지연 무시 */ },
                block = {
                    attemptCount++
                    throw RuntimeException("fail")
                }
            )
        }
        assertEquals(1, attemptCount)
    }

    @Test
    fun `음수 attempt에 대한 delayForAttempt는 0을 반환한다`() {
        val policy = RetryPolicy(maxAttempts = 3, initialDelayMillis = 100)
        assertEquals(0, policy.delayForAttempt(-1))
    }

    @Test
    fun `RetryPolicy 검증 실패 - maxAttempts가 0이면 예외를 던진다`() {
        assertThrows(IllegalArgumentException::class.java) {
            RetryPolicy(maxAttempts = 0, initialDelayMillis = 100)
        }
    }

    @Test
    fun `RetryPolicy 검증 실패 - maxAttempts가 음수이면 예외를 던진다`() {
        assertThrows(IllegalArgumentException::class.java) {
            RetryPolicy(maxAttempts = -1, initialDelayMillis = 100)
        }
    }

    @Test
    fun `RetryPolicy 검증 실패 - initialDelayMillis가 음수이면 예외를 던진다`() {
        assertThrows(IllegalArgumentException::class.java) {
            RetryPolicy(maxAttempts = 3, initialDelayMillis = -1)
        }
    }

    @Test
    fun `multiplier가 1 0이면 지연 시간이 일정하게 유지된다`() {
        val policy = RetryPolicy(
            maxAttempts = 5,
            initialDelayMillis = 100,
            multiplier = 1.0
        )
        assertEquals(100, policy.delayForAttempt(0))
        assertEquals(100, policy.delayForAttempt(1))
        assertEquals(100, policy.delayForAttempt(2))
    }

    @Test
    fun `큰 multiplier 값으로 빠른 지수 증가를 확인한다`() {
        val policy = RetryPolicy(
            maxAttempts = 5,
            initialDelayMillis = 1,
            multiplier = 10.0,
            maxDelayMillis = 100000
        )
        assertEquals(1, policy.delayForAttempt(0))       // 1 * 10^0 = 1
        assertEquals(10, policy.delayForAttempt(1))      // 1 * 10^1 = 10
        assertEquals(100, policy.delayForAttempt(2))     // 1 * 10^2 = 100
        assertEquals(1000, policy.delayForAttempt(3))    // 1 * 10^3 = 1000
    }
}
