package cc.midolog.gateway.filter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono

class RequestIdFilterTest {

    private val filter = RequestIdFilter()

    @Test
    fun `generates X-Request-Id when absent`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"))
        filter.filter(exchange) { Mono.empty() }.block()
        assertNotNull(exchange.response.headers.getFirst("X-Request-Id"))
    }

    @Test
    fun `keeps existing X-Request-Id`() {
        val given = "fixed-request-id"
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test").header("X-Request-Id", given)
        )
        filter.filter(exchange) { Mono.empty() }.block()
        assertEquals(given, exchange.response.headers.getFirst("X-Request-Id"))
    }

    @Test
    fun `regenerates X-Request-Id when format is invalid`() {
        val malicious = "bad id\nInjected-Log-Line"
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test").header("X-Request-Id", malicious)
        )
        filter.filter(exchange) { Mono.empty() }.block()
        val result = exchange.response.headers.getFirst("X-Request-Id")
        assertNotNull(result)
        assertNotEquals(malicious, result)
    }
}
