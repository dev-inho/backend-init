package cc.midolog.gateway.circuitbreaker

import cc.midolog.gateway.config.GatewayCircuitBreakerProperties
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}

sealed class CircuitBreakerException(message: String) : RuntimeException(message)

class CircuitBreakerOpenException(
    val targetUrl: String,
    val state: CircuitBreakerState = CircuitBreakerState.OPEN
) : CircuitBreakerException("Circuit breaker is $state for target: $targetUrl")

class CircuitBreakerConcurrencyException(
    val targetUrl: String
) : CircuitBreakerException("Concurrent execution limit exceeded for target: $targetUrl")

interface CircuitBreakerPermit {
    fun recordSuccess()
    fun recordFailure()
    fun release()
}

/**
 * 타깃 URL별 연속 실패율 및 동시 실행량을 감시하여 다운스트림 장애를 격리하는 서킷 브레이커.
 *
 * CLOSED, OPEN, HALF_OPEN 3대 상태 모델과 세대(generation) 기반 상태 소유권을 제공하여,
 * 이전 세대에서 지연된 응답이나 취소가 현재 회로의 실패 횟수, 대기 시각, 탐색 허가 상태를 오염시키지 않도록 방어한다.
 */
class CircuitBreaker(
    val targetUrl: String,
    private val properties: GatewayCircuitBreakerProperties,
    private val clock: Clock
) {
    private val lock = Any()

    @Volatile
    var state: CircuitBreakerState = CircuitBreakerState.CLOSED
        private set

    private var failureStreak: Int = 0

    @Volatile
    var openedAt: Instant = Instant.MIN
        private set

    private var generation: Long = 0L

    private val inFlightCalls = AtomicInteger(0)
    private var halfOpenInFlight: Int = 0

    fun inFlightCount(): Int = inFlightCalls.get()

    fun failureStreak(): Int = synchronized(lock) { failureStreak }

    fun currentState(): CircuitBreakerState {
        if (!properties.enabled) return CircuitBreakerState.CLOSED
        val now = clock.instant()
        synchronized(lock) {
            checkTransition(now)
            return state
        }
    }

    fun acquire(): CircuitBreakerPermit {
        if (!properties.enabled) return NoOpPermit

        val now = clock.instant()
        synchronized(lock) {
            checkTransition(now)

            return when (state) {
                CircuitBreakerState.CLOSED -> {
                    if (inFlightCalls.get() >= properties.maxConcurrentCalls) {
                        throw CircuitBreakerConcurrencyException(targetUrl)
                    }
                    inFlightCalls.incrementAndGet()
                    ClosedPermit(generation)
                }
                CircuitBreakerState.OPEN -> {
                    throw CircuitBreakerOpenException(targetUrl, CircuitBreakerState.OPEN)
                }
                CircuitBreakerState.HALF_OPEN -> {
                    if (halfOpenInFlight < properties.halfOpenPermits) {
                        halfOpenInFlight++
                        inFlightCalls.incrementAndGet()
                        HalfOpenPermit(generation)
                    } else {
                        throw CircuitBreakerOpenException(targetUrl, CircuitBreakerState.HALF_OPEN)
                    }
                }
            }
        }
    }

    private fun checkTransition(now: Instant) {
        if (state == CircuitBreakerState.OPEN) {
            val elapsed = Duration.between(openedAt, now)
            if (elapsed >= properties.openDuration) {
                state = CircuitBreakerState.HALF_OPEN
                halfOpenInFlight = 0
                generation++
            }
        }
    }

    fun <T : Any> execute(action: Mono<T>, isFailure: (Throwable) -> Boolean = { true }): Mono<T> =
        Mono.defer {
            val permit = acquire()
            action
                .doOnSuccess {
                    permit.recordSuccess()
                }
                .doOnError { error ->
                    if (isFailure(error)) {
                        permit.recordFailure()
                    }
                }
                .doFinally { _ ->
                    permit.release()
                }
        }

    private inner class ClosedPermit(
        private val permitGeneration: Long
    ) : CircuitBreakerPermit {
        private val recorded = AtomicBoolean(false)
        private val released = AtomicBoolean(false)

        override fun recordSuccess() {
            if (recorded.compareAndSet(false, true)) {
                synchronized(lock) {
                    if (state == CircuitBreakerState.CLOSED && generation == permitGeneration) {
                        failureStreak = 0
                    }
                }
            }
        }

        override fun recordFailure() {
            if (recorded.compareAndSet(false, true)) {
                val now = clock.instant()
                synchronized(lock) {
                    if (state == CircuitBreakerState.CLOSED && generation == permitGeneration) {
                        failureStreak++
                        if (failureStreak >= properties.failureThreshold) {
                            state = CircuitBreakerState.OPEN
                            openedAt = now
                            generation++
                        }
                    }
                }
            }
        }

        override fun release() {
            if (released.compareAndSet(false, true)) {
                inFlightCalls.decrementAndGet()
            }
        }
    }

    private inner class HalfOpenPermit(
        private val permitGeneration: Long
    ) : CircuitBreakerPermit {
        private val recorded = AtomicBoolean(false)
        private val released = AtomicBoolean(false)

        override fun recordSuccess() {
            if (recorded.compareAndSet(false, true)) {
                synchronized(lock) {
                    if (state == CircuitBreakerState.HALF_OPEN && generation == permitGeneration) {
                        state = CircuitBreakerState.CLOSED
                        failureStreak = 0
                        halfOpenInFlight = 0
                        generation++
                    }
                }
            }
        }

        override fun recordFailure() {
            if (recorded.compareAndSet(false, true)) {
                val now = clock.instant()
                synchronized(lock) {
                    if (state == CircuitBreakerState.HALF_OPEN && generation == permitGeneration) {
                        state = CircuitBreakerState.OPEN
                        openedAt = now
                        halfOpenInFlight = 0
                        generation++
                    }
                }
            }
        }

        override fun release() {
            if (released.compareAndSet(false, true)) {
                synchronized(lock) {
                    if (state == CircuitBreakerState.HALF_OPEN && generation == permitGeneration) {
                        halfOpenInFlight = maxOf(0, halfOpenInFlight - 1)
                    }
                }
                inFlightCalls.decrementAndGet()
            }
        }
    }

    private object NoOpPermit : CircuitBreakerPermit {
        override fun recordSuccess() {}
        override fun recordFailure() {}
        override fun release() {}
    }
}
