package cc.midolog.gateway.circuitbreaker

import cc.midolog.gateway.config.GatewayCircuitBreakerProperties
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CircuitBreakerTest {

    private class MutableClock(private var current: Instant = Instant.parse("2026-09-19T00:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = current
        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    @Test
    fun `initial state is CLOSED and transitions to OPEN after threshold failures`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(failureThreshold = 3, openDuration = Duration.ofSeconds(5))
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        assertEquals(CircuitBreakerState.CLOSED, breaker.currentState())
        assertEquals(0, breaker.failureStreak())

        // 1st failure
        breaker.execute(Mono.error<String>(RuntimeException("error-1"))).onErrorResume { Mono.empty() }.block()
        assertEquals(CircuitBreakerState.CLOSED, breaker.currentState())
        assertEquals(1, breaker.failureStreak())

        // Success resets failure streak
        breaker.execute(Mono.just("ok")).block()
        assertEquals(CircuitBreakerState.CLOSED, breaker.currentState())
        assertEquals(0, breaker.failureStreak())

        // 3 consecutive failures
        breaker.execute(Mono.error<String>(RuntimeException("error-1"))).onErrorResume { Mono.empty() }.block()
        breaker.execute(Mono.error<String>(RuntimeException("error-2"))).onErrorResume { Mono.empty() }.block()
        breaker.execute(Mono.error<String>(RuntimeException("error-3"))).onErrorResume { Mono.empty() }.block()

        assertEquals(CircuitBreakerState.OPEN, breaker.currentState())
        assertEquals(3, breaker.failureStreak())
        assertEquals(clock.instant(), breaker.openedAt)
    }

    @Test
    fun `OPEN circuit rejects calls without invoking action and does not extend open window`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(failureThreshold = 2, openDuration = Duration.ofSeconds(5))
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        breaker.execute(Mono.error<String>(RuntimeException("fail-1"))).onErrorResume { Mono.empty() }.block()
        breaker.execute(Mono.error<String>(RuntimeException("fail-2"))).onErrorResume { Mono.empty() }.block()
        assertEquals(CircuitBreakerState.OPEN, breaker.currentState())

        val openTime = breaker.openedAt

        // 2 seconds later, still OPEN
        clock.advance(Duration.ofSeconds(2))

        var invoked = false
        val exception = assertThrows<CircuitBreakerOpenException> {
            breaker.execute(Mono.fromCallable {
                invoked = true
                "never-reached"
            }).block()
        }

        assertFalse(invoked, "Action must not be invoked when circuit is OPEN")
        assertEquals("http://target.internal", exception.targetUrl)
        assertEquals(openTime, breaker.openedAt, "Rejection must not extend openedAt timestamp")
    }

    @Test
    fun `transitions to HALF_OPEN after waitDuration and recovers to CLOSED on probe success`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(failureThreshold = 1, openDuration = Duration.ofSeconds(5), halfOpenPermits = 1)
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        breaker.execute(Mono.error<String>(RuntimeException("fail"))).onErrorResume { Mono.empty() }.block()
        assertEquals(CircuitBreakerState.OPEN, breaker.currentState())

        // Advance past openDuration
        clock.advance(Duration.ofSeconds(6))
        assertEquals(CircuitBreakerState.HALF_OPEN, breaker.currentState())

        // Probe succeeds
        var probeInvoked = false
        val result = breaker.execute(Mono.fromCallable {
            probeInvoked = true
            "probe-success"
        }).block()

        assertTrue(probeInvoked)
        assertEquals("probe-success", result)
        assertEquals(CircuitBreakerState.CLOSED, breaker.currentState())
        assertEquals(0, breaker.failureStreak())
    }

    @Test
    fun `HALF_OPEN probe failure transitions back to OPEN with fresh openedAt`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(failureThreshold = 1, openDuration = Duration.ofSeconds(5), halfOpenPermits = 1)
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        breaker.execute(Mono.error<String>(RuntimeException("fail"))).onErrorResume { Mono.empty() }.block()
        val firstOpenedAt = breaker.openedAt

        clock.advance(Duration.ofSeconds(10))
        assertEquals(CircuitBreakerState.HALF_OPEN, breaker.currentState())

        // Probe fails
        breaker.execute(Mono.error<String>(RuntimeException("probe-fail"))).onErrorResume { Mono.empty() }.block()

        assertEquals(CircuitBreakerState.OPEN, breaker.currentState())
        assertEquals(clock.instant(), breaker.openedAt)
        assertTrue(breaker.openedAt > firstOpenedAt)
    }

    @Test
    fun `HALF_OPEN rejects concurrent calls exceeding halfOpenPermits`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(failureThreshold = 1, openDuration = Duration.ofSeconds(5), halfOpenPermits = 1)
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        breaker.execute(Mono.error<String>(RuntimeException("fail"))).onErrorResume { Mono.empty() }.block()
        clock.advance(Duration.ofSeconds(6))

        val permit1 = breaker.acquire()
        try {
            assertThrows<CircuitBreakerOpenException> {
                breaker.acquire()
            }
        } finally {
            permit1.release()
        }
    }

    @Test
    fun `enforces maxConcurrentCalls limit and releases on completion`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(maxConcurrentCalls = 2)
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        val permit1 = breaker.acquire()
        val permit2 = breaker.acquire()
        assertEquals(2, breaker.inFlightCount())

        assertThrows<CircuitBreakerConcurrencyException> {
            breaker.acquire()
        }

        permit1.release()
        assertEquals(1, breaker.inFlightCount())

        // Now acquire succeeds
        val permit3 = breaker.acquire()
        assertEquals(2, breaker.inFlightCount())

        permit2.release()
        permit3.release()
        assertEquals(0, breaker.inFlightCount())
    }

    @Test
    fun `cancellation releases permit without recording failure`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(maxConcurrentCalls = 1, failureThreshold = 1)
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        val cancelMono = breaker.execute(Mono.never<String>())
        val subscription = cancelMono.subscribe()

        assertEquals(1, breaker.inFlightCount())
        assertEquals(0, breaker.failureStreak())
        assertEquals(CircuitBreakerState.CLOSED, breaker.currentState())

        subscription.dispose() // cancel

        assertEquals(0, breaker.inFlightCount())
        assertEquals(0, breaker.failureStreak())
        assertEquals(CircuitBreakerState.CLOSED, breaker.currentState())
    }

    @Test
    fun `registry isolates circuit breakers by target URL`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(failureThreshold = 1)
        val registry = CircuitBreakerRegistry(properties, clock)

        val breakerA = registry.getOrCreate("http://app-a.internal")
        val breakerB = registry.getOrCreate("http://app-b.internal")

        breakerA.execute(Mono.error<String>(RuntimeException("fail"))).onErrorResume { Mono.empty() }.block()

        assertEquals(CircuitBreakerState.OPEN, breakerA.currentState())
        assertEquals(CircuitBreakerState.CLOSED, breakerB.currentState())
    }
}
