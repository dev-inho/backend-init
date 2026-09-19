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

    @Test
    fun `late failure from CLOSED permit does not alter HALF_OPEN or extend open window`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(failureThreshold = 2, openDuration = Duration.ofSeconds(5))
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        // 1. Acquired in CLOSED generation 0
        val delayedPermit = breaker.acquire()
        assertEquals(1, breaker.inFlightCount())

        // 2. Circuit transitions to OPEN (generation 1) via other requests
        breaker.execute(Mono.error<String>(RuntimeException("fail-1"))).onErrorResume { Mono.empty() }.block()
        breaker.execute(Mono.error<String>(RuntimeException("fail-2"))).onErrorResume { Mono.empty() }.block()
        assertEquals(CircuitBreakerState.OPEN, breaker.currentState())
        val originalOpenedAt = breaker.openedAt

        // 3. Time advances past openDuration into HALF_OPEN (generation 2)
        clock.advance(Duration.ofSeconds(6))
        assertEquals(CircuitBreakerState.HALF_OPEN, breaker.currentState())

        // 4. Delayed failure arrives from the original CLOSED permit (generation 0)
        delayedPermit.recordFailure()

        // 5. Must NOT transition to OPEN, must NOT reset openedAt, must stay HALF_OPEN
        assertEquals(CircuitBreakerState.HALF_OPEN, breaker.currentState(), "Late CLOSED failure must not force HALF_OPEN back to OPEN")
        assertEquals(originalOpenedAt, breaker.openedAt, "Late CLOSED failure must not modify openedAt")

        // 6. Legitimate probe can now succeed and recover circuit to CLOSED (generation 3)
        breaker.execute(Mono.just("probe-ok")).block()
        assertEquals(CircuitBreakerState.CLOSED, breaker.currentState())
        assertEquals(0, breaker.failureStreak())

        delayedPermit.release()
        assertEquals(0, breaker.inFlightCount())
    }

    @Test
    fun `late failure from CLOSED permit does not pollute new CLOSED failure streak`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(failureThreshold = 2, openDuration = Duration.ofSeconds(5))
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        // Generation 0 permit
        val delayedPermit = breaker.acquire()

        // Transition CLOSED -> OPEN -> HALF_OPEN -> CLOSED (generation 3)
        breaker.execute(Mono.error<String>(RuntimeException("fail-1"))).onErrorResume { Mono.empty() }.block()
        breaker.execute(Mono.error<String>(RuntimeException("fail-2"))).onErrorResume { Mono.empty() }.block()
        clock.advance(Duration.ofSeconds(6))
        assertEquals(CircuitBreakerState.HALF_OPEN, breaker.currentState())
        breaker.execute(Mono.just("probe-ok")).block()
        assertEquals(CircuitBreakerState.CLOSED, breaker.currentState())
        assertEquals(0, breaker.failureStreak())

        // Delayed failure from generation 0 arrives
        delayedPermit.recordFailure()

        // New CLOSED state must NOT have failureStreak incremented
        assertEquals(0, breaker.failureStreak(), "Late failure from previous cycle must not increment new CLOSED failureStreak")
        assertEquals(CircuitBreakerState.CLOSED, breaker.currentState())

        delayedPermit.release()
        assertEquals(0, breaker.inFlightCount())
    }

    @Test
    fun `late success from CLOSED permit does not reset failure streak of new CLOSED state`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(failureThreshold = 3, openDuration = Duration.ofSeconds(5))
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        // Generation 0 permit
        val delayedSuccessPermit = breaker.acquire()

        // Transition CLOSED -> OPEN -> HALF_OPEN -> CLOSED
        breaker.execute(Mono.error<String>(RuntimeException("fail-1"))).onErrorResume { Mono.empty() }.block()
        breaker.execute(Mono.error<String>(RuntimeException("fail-2"))).onErrorResume { Mono.empty() }.block()
        breaker.execute(Mono.error<String>(RuntimeException("fail-3"))).onErrorResume { Mono.empty() }.block()
        clock.advance(Duration.ofSeconds(6))
        breaker.execute(Mono.just("probe-ok")).block()
        assertEquals(CircuitBreakerState.CLOSED, breaker.currentState())

        // In new CLOSED state, a new failure occurs (failureStreak = 1)
        breaker.execute(Mono.error<String>(RuntimeException("new-fail"))).onErrorResume { Mono.empty() }.block()
        assertEquals(1, breaker.failureStreak())

        // Delayed success from old generation 0 arrives
        delayedSuccessPermit.recordSuccess()

        // Must NOT reset failureStreak back to 0
        assertEquals(1, breaker.failureStreak(), "Late success from previous cycle must not reset active failureStreak")

        delayedSuccessPermit.release()
        assertEquals(0, breaker.inFlightCount())
    }

    @Test
    fun `late response from previous HALF_OPEN probe does not overwrite newer OPEN state`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(failureThreshold = 1, openDuration = Duration.ofSeconds(5), halfOpenPermits = 2)
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        breaker.execute(Mono.error<String>(RuntimeException("initial-fail"))).onErrorResume { Mono.empty() }.block()
        clock.advance(Duration.ofSeconds(6))
        assertEquals(CircuitBreakerState.HALF_OPEN, breaker.currentState())

        // Probe 1 and 2 acquired in HALF_OPEN (generation 2)
        val probe1 = breaker.acquire()
        val probe2 = breaker.acquire()

        // Probe 2 fails first, transitioning circuit to OPEN (generation 3) with fresh openedAt
        clock.advance(Duration.ofSeconds(1))
        val expectedOpenedAt = clock.instant()
        probe2.recordFailure()
        probe2.release()
        assertEquals(CircuitBreakerState.OPEN, breaker.currentState())
        assertEquals(expectedOpenedAt, breaker.openedAt)

        // Probe 1 completes late (after circuit is already OPEN)
        clock.advance(Duration.ofSeconds(2))
        probe1.recordSuccess() // Late success from obsolete probe
        probe1.release()

        // Circuit must remain OPEN and openedAt must remain unchanged
        assertEquals(CircuitBreakerState.OPEN, breaker.currentState(), "Late probe success must not force OPEN back to CLOSED")
        assertEquals(expectedOpenedAt, breaker.openedAt, "Late probe response must not modify openedAt")
        assertEquals(0, breaker.inFlightCount())
    }

    @Test
    fun `delayed release of cancelled or completed half-open probe does not corrupt next generation permits`() {
        val clock = MutableClock()
        val properties = GatewayCircuitBreakerProperties(failureThreshold = 1, openDuration = Duration.ofSeconds(5), halfOpenPermits = 1)
        val breaker = CircuitBreaker("http://target.internal", properties, clock)

        breaker.execute(Mono.error<String>(RuntimeException("fail"))).onErrorResume { Mono.empty() }.block()
        clock.advance(Duration.ofSeconds(6))
        assertEquals(CircuitBreakerState.HALF_OPEN, breaker.currentState())

        // Generation 2 probe acquired
        val probeOld = breaker.acquire()
        probeOld.recordFailure() // Transitions to OPEN (generation 3)
        assertEquals(CircuitBreakerState.OPEN, breaker.currentState())

        // Time advances to next HALF_OPEN (generation 4)
        clock.advance(Duration.ofSeconds(6))
        assertEquals(CircuitBreakerState.HALF_OPEN, breaker.currentState())

        // Delayed release of old probe happens now
        probeOld.release()

        // New HALF_OPEN should still allow exactly 1 permit and reject the 2nd
        val probeNew = breaker.acquire()
        assertThrows<CircuitBreakerOpenException> {
            breaker.acquire()
        }

        probeNew.recordSuccess()
        probeNew.release()
        assertEquals(CircuitBreakerState.CLOSED, breaker.currentState())
        assertEquals(0, breaker.inFlightCount())
    }
}
