package cc.midolog.gateway.filter

import cc.midolog.gateway.ratelimit.RateLimiter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import java.net.InetSocketAddress

class AuthTokenRateLimitFilterTest {

    /** 호출 여부/횟수를 기록하는 fake RateLimiter. 모킹 라이브러리 없이 동작을 주입한다. */
    private class FakeRateLimiter(private val result: () -> Mono<Boolean>) : RateLimiter {
        var invocationCount = 0
            private set

        override fun tryAcquire(key: String): Mono<Boolean> {
            invocationCount++
            return result()
        }
    }

    private fun requestFor(method: HttpMethod, path: String) =
        MockServerHttpRequest.method(method, path)
            .remoteAddress(InetSocketAddress("127.0.0.1", 12345))
            .build()

    @Test
    fun `passes request when under the limit`() {
        val limiter = FakeRateLimiter { Mono.just(true) }
        val filter = AuthTokenRateLimitFilter(limiter)
        val exchange = MockServerWebExchange.from(requestFor(HttpMethod.POST, "/api/auth/token"))

        filter.filter(exchange) { Mono.empty() }.block()

        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.response.statusCode)
        assertEquals(1, limiter.invocationCount)
    }

    @Test
    fun `returns 429 when the limit is exceeded`() {
        val limiter = FakeRateLimiter { Mono.just(false) }
        val filter = AuthTokenRateLimitFilter(limiter)
        val exchange = MockServerWebExchange.from(requestFor(HttpMethod.POST, "/api/auth/token"))

        filter.filter(exchange) { Mono.empty() }.block()

        assertEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.response.statusCode)
    }

    @Test
    fun `does not limit GET requests to the token endpoint`() {
        val limiter = FakeRateLimiter { Mono.just(false) }
        val filter = AuthTokenRateLimitFilter(limiter)
        val exchange = MockServerWebExchange.from(requestFor(HttpMethod.GET, "/api/auth/token"))

        filter.filter(exchange) { Mono.empty() }.block()

        assertEquals(0, limiter.invocationCount)
        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.response.statusCode)
    }

    @Test
    fun `does not limit other api paths`() {
        val limiter = FakeRateLimiter { Mono.just(false) }
        val filter = AuthTokenRateLimitFilter(limiter)
        val exchange = MockServerWebExchange.from(requestFor(HttpMethod.POST, "/api/sample/ping"))

        filter.filter(exchange) { Mono.empty() }.block()

        assertEquals(0, limiter.invocationCount)
    }

    @Test
    fun `does not limit actuator paths`() {
        val limiter = FakeRateLimiter { Mono.just(false) }
        val filter = AuthTokenRateLimitFilter(limiter)
        val exchange = MockServerWebExchange.from(requestFor(HttpMethod.GET, "/actuator/health"))

        filter.filter(exchange) { Mono.empty() }.block()

        assertEquals(0, limiter.invocationCount)
    }

    @Test
    fun `does not limit batch paths`() {
        val limiter = FakeRateLimiter { Mono.just(false) }
        val filter = AuthTokenRateLimitFilter(limiter)
        val exchange = MockServerWebExchange.from(requestFor(HttpMethod.POST, "/batch/run"))

        filter.filter(exchange) { Mono.empty() }.block()

        assertEquals(0, limiter.invocationCount)
    }

    @Test
    fun `fails open when the rate limiter errors`() {
        val limiter = FakeRateLimiter { Mono.error(RuntimeException("redis down")) }
        val filter = AuthTokenRateLimitFilter(limiter)
        val exchange = MockServerWebExchange.from(requestFor(HttpMethod.POST, "/api/auth/token"))

        filter.filter(exchange) { Mono.empty() }.block()

        assertNotEquals(HttpStatus.TOO_MANY_REQUESTS, exchange.response.statusCode)
    }
}
