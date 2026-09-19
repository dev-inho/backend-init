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
                    ClosedPermit()
                }
                CircuitBreakerState.OPEN -> {
                    throw CircuitBreakerOpenException(targetUrl, CircuitBreakerState.OPEN)
                }
                CircuitBreakerState.HALF_OPEN -> {
                    if (halfOpenInFlight < properties.halfOpenPermits) {
                        halfOpenInFlight++
                        inFlightCalls.incrementAndGet()
                        HalfOpenPermit()
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

    private inner class ClosedPermit : CircuitBreakerPermit {
        private val recorded = AtomicBoolean(false)
        private val released = AtomicBoolean(false)

        override fun recordSuccess() {
            if (recorded.compareAndSet(false, true)) {
                synchronized(lock) {
                    failureStreak = 0
                }
            }
        }

        override fun recordFailure() {
            if (recorded.compareAndSet(false, true)) {
                val now = clock.instant()
                synchronized(lock) {
                    failureStreak++
                    if (failureStreak >= properties.failureThreshold) {
                        state = CircuitBreakerState.OPEN
                        openedAt = now
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

    private inner class HalfOpenPermit : CircuitBreakerPermit {
        private val recorded = AtomicBoolean(false)
        private val released = AtomicBoolean(false)

        override fun recordSuccess() {
            if (recorded.compareAndSet(false, true)) {
                synchronized(lock) {
                    state = CircuitBreakerState.CLOSED
                    failureStreak = 0
                    halfOpenInFlight = 0
                }
            }
        }

        override fun recordFailure() {
            if (recorded.compareAndSet(false, true)) {
                val now = clock.instant()
                synchronized(lock) {
                    state = CircuitBreakerState.OPEN
                    openedAt = now
                    halfOpenInFlight = 0
                }
            }
        }

        override fun release() {
            if (released.compareAndSet(false, true)) {
                synchronized(lock) {
                    halfOpenInFlight = maxOf(0, halfOpenInFlight - 1)
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
