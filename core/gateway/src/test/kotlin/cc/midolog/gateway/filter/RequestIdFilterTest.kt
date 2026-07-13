package cc.midolog.gateway.filter

import cc.midolog.logging.LoggingMdc
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
    fun `HEADER constant is aligned with the standard LoggingMdc REQUEST_ID key`() {
        assertEquals(LoggingMdc.REQUEST_ID, RequestIdFilter.HEADER)
    }

    @Test
    fun `generates X-Request-Id when absent`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"))
        filter.filter(exchange) { Mono.empty() }.block()
        assertNotNull(exchange.response.headers.getFirst(LoggingMdc.REQUEST_ID))
    }

    @Test
    fun `keeps existing X-Request-Id`() {
        val given = "fixed-request-id"
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test").header(LoggingMdc.REQUEST_ID, given)
        )
        filter.filter(exchange) { Mono.empty() }.block()
        assertEquals(given, exchange.response.headers.getFirst(LoggingMdc.REQUEST_ID))
    }

    @Test
    fun `regenerates X-Request-Id when format is invalid`() {
        val malicious = "bad id\nInjected-Log-Line"
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test").header(LoggingMdc.REQUEST_ID, malicious)
        )
        filter.filter(exchange) { Mono.empty() }.block()
        val result = exchange.response.headers.getFirst(LoggingMdc.REQUEST_ID)
        assertNotNull(result)
        assertNotEquals(malicious, result)
    }

    @Test
    fun `propagates X-Request-Id to the downstream request headers`() {
        val given = "downstream-request-id"
        var downstreamHeader: String? = null
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/test").header(LoggingMdc.REQUEST_ID, given)
        )

        filter.filter(exchange) { mutatedExchange ->
            downstreamHeader = mutatedExchange.request.headers.getFirst(LoggingMdc.REQUEST_ID)
            Mono.empty()
        }.block()

        assertEquals(given, downstreamHeader)
    }
}
