package cc.midolog.logging

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import reactor.core.publisher.Flux
import reactor.test.StepVerifier

class ReactorMdcTest {

    @BeforeEach
    fun setUp() {
        MDC.clear()
        ReactorMdc.enable()
    }

    @Test
    fun `copies Reactor Context X-Request-Id into MDC while emitting`() {
        val observedDuringEmission = mutableListOf<String?>()

        Flux.just(1, 2, 3)
            .doOnNext { observedDuringEmission.add(MDC.get(ReactorMdc.KEY)) }
            .contextWrite { ctx -> ctx.put(ReactorMdc.KEY, "test-request-id") }
            .`as` { StepVerifier.create(it) }
            .expectNext(1, 2, 3)
            .verifyComplete()

        assertEquals(listOf("test-request-id", "test-request-id", "test-request-id"), observedDuringEmission)
    }

    @Test
    fun `does not leak X-Request-Id into MDC after signal processing completes`() {
        Flux.just("a")
            .contextWrite { ctx -> ctx.put(ReactorMdc.KEY, "leak-check-id") }
            .`as` { StepVerifier.create(it) }
            .expectNext("a")
            .verifyComplete()

        assertNull(MDC.get(ReactorMdc.KEY), "MDC must not retain the request id after the reactor chain completes")
    }

    @Test
    fun `does not put anything into MDC when the Context has no X-Request-Id`() {
        val observedDuringEmission = mutableListOf<String?>()

        Flux.just(1)
            .doOnNext { observedDuringEmission.add(MDC.get(ReactorMdc.KEY)) }
            .`as` { StepVerifier.create(it) }
            .expectNext(1)
            .verifyComplete()

        assertEquals(listOf<String?>(null), observedDuringEmission)
    }

    @Test
    fun `isolates X-Request-Id between concurrently subscribed Reactor chains`() {
        val firstObserved = mutableListOf<String?>()
        val secondObserved = mutableListOf<String?>()

        val first = Flux.just(1, 2)
            .doOnNext { firstObserved.add(MDC.get(ReactorMdc.KEY)) }
            .contextWrite { ctx -> ctx.put(ReactorMdc.KEY, "first-id") }

        val second = Flux.just(1, 2)
            .doOnNext { secondObserved.add(MDC.get(ReactorMdc.KEY)) }
            .contextWrite { ctx -> ctx.put(ReactorMdc.KEY, "second-id") }

        StepVerifier.create(first).expectNext(1, 2).verifyComplete()
        StepVerifier.create(second).expectNext(1, 2).verifyComplete()

        assertEquals(listOf("first-id", "first-id"), firstObserved)
        assertEquals(listOf("second-id", "second-id"), secondObserved)
    }
}
