package cc.midolog.gateway.proxy

import cc.midolog.gateway.config.GatewayRouteProperties
import cc.midolog.gateway.config.GatewayRetryProperties
import cc.midolog.gateway.route.GatewayRouteSelector
import cc.midolog.logging.LoggingMdc
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.ExchangeFunction
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.HandlerStrategies
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Duration
import java.util.concurrent.TimeoutException
import java.net.ConnectException

class ProxyHandlerTest {

    private val bufferFactory = DefaultDataBufferFactory()

    @Test
    fun `forwards path query and sanitized headers to application route`() {
        var captured: ClientRequest? = null
        val handler = handlerWith { request ->
            captured = request
            Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build())
        }
        val serverRequest = serverRequest(
            MockServerHttpRequest.method(HttpMethod.GET, URI.create("/api/sample/items?name=a%20b&tag=one"))
                .header(LoggingMdc.REQUEST_ID, "request-123")
                .header(HttpHeaders.HOST, "external.example")
                .header(HttpHeaders.CONNECTION, "keep-alive, X-Hop-Only")
                .header("X-Hop-Only", "must-not-forward")
                .build(),
        )

        handler.proxy(serverRequest).block()

        val downstream = requireNotNull(captured)
        assertEquals("http://application.internal/api/sample/items?name=a%20b&tag=one", downstream.url().toString())
        assertEquals("request-123", downstream.headers().getFirst(LoggingMdc.REQUEST_ID))
        assertFalse(downstream.headers().containsHeader(HttpHeaders.HOST))
        assertFalse(downstream.headers().containsHeader(HttpHeaders.CONNECTION))
        assertFalse(downstream.headers().containsHeader("X-Hop-Only"))
    }

    @Test
    fun `buffers downstream response body and sanitizes response headers`() {
        val handler = handlerWith {
            val body: Flux<DataBuffer> = Flux.just(
                bufferFactory.wrap("chunk-1".toByteArray()),
                bufferFactory.wrap("chunk-2".toByteArray()),
            )
            Mono.just(
                ClientResponse.create(HttpStatus.ACCEPTED)
                    .header("X-Downstream", "ok")
                    .header(HttpHeaders.CONTENT_LENGTH, "999")
                    .body(body)
                    .build(),
            )
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/stream"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        assertNotNull(response)

        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(HttpStatus.ACCEPTED, exchange.response.statusCode)
        assertEquals("ok", exchange.response.headers.getFirst("X-Downstream"))
        assertFalse(exchange.response.headers[HttpHeaders.CONTENT_LENGTH].orEmpty().contains("999"))
        assertEquals("chunk-1chunk-2", exchange.response.bodyAsString.block())
    }

    @Test
    fun `returns gateway timeout when downstream call times out`() {
        val handler = handlerWith {
            Mono.error(TimeoutException("downstream timed out"))
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/slow"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        assertNotNull(response)

        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.response.statusCode)
    }

    @Test
    fun `routes batch path to batch target`() {
        var captured: ClientRequest? = null
        val handler = handlerWith { request ->
            captured = request
            Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build())
        }

        handler.proxy(serverRequest(MockServerHttpRequest.post("/batch/jobs/run?dryRun=true").build())).block()

        assertEquals("http://batch.internal/batch/jobs/run?dryRun=true", requireNotNull(captured).url().toString())
    }

    @Test
    fun `GET retrieves 200 after 503 retry`() {
        var callCount = 0
        val handler = handlerWith(maxAttempts = 3) {
            callCount++
            if (callCount == 1) {
                Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build())
            } else {
                Mono.just(ClientResponse.create(HttpStatus.OK).build())
            }
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(2, callCount)
        assertEquals(HttpStatus.OK, exchange.response.statusCode)
    }

    @Test
    fun `POST does not retry on 503 and returns 502 according to policy`() {
        var callCount = 0
        val handler = handlerWith(maxAttempts = 3) {
            callCount++
            Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build())
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/retry"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(1, callCount)
        assertEquals(HttpStatus.BAD_GATEWAY, exchange.response.statusCode)
    }

    @Test
    fun `Returns gateway policy status when max-attempts exhausted`() {
        var callCount = 0
        val handler = handlerWith(maxAttempts = 3) {
            callCount++
            Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build())
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(3, callCount)
        assertEquals(HttpStatus.BAD_GATEWAY, exchange.response.statusCode)
    }

    @Test
    fun `Retries on connection failure and TimeoutException only for idempotent requests`() {
        var callCount = 0
        val handler = handlerWith(maxAttempts = 2) {
            callCount++
            Mono.error(ConnectException("Connection refused"))
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(2, callCount)
        assertEquals(HttpStatus.BAD_GATEWAY, exchange.response.statusCode)

        var postCallCount = 0
        val postHandler = handlerWith(maxAttempts = 2) {
            postCallCount++
            Mono.error(TimeoutException("Timeout"))
        }
        val postExchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/retry"))
        val postResponse = postHandler.proxy(serverRequest(postExchange.request)).block()
        postResponse!!.writeTo(postExchange, responseContext()).block()

        assertEquals(1, postCallCount)
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, postExchange.response.statusCode)
    }

    @Test
    fun `Does not retry on 500 or 501`() {
        var callCount = 0
        val handler = handlerWith(maxAttempts = 3) {
            callCount++
            Mono.just(ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).build())
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(1, callCount)
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exchange.response.statusCode)
    }

    @Test
    fun `Records metrics with SimpleMeterRegistry`() {
        val registry = SimpleMeterRegistry()
        var callCount = 0
        val handler = handlerWith(maxAttempts = 3, meterRegistry = registry) {
            callCount++
            if (callCount == 1) {
                Mono.error(TimeoutException("timeout"))
            } else {
                Mono.just(ClientResponse.create(HttpStatus.OK).build())
            }
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        val requests = registry.get("gateway.proxy.requests").counter()
        assertEquals(1.0, requests.count())
        assertEquals("http://application.internal", requests.id.getTag("target"))
        assertEquals("200", requests.id.getTag("status"))
        assertEquals("true", requests.id.getTag("retried"))

        val latency = registry.get("gateway.proxy.latency").timer()
        assertEquals(1L, latency.count())
    }

    @Test
    fun `Works without MeterRegistry without exceptions`() {
        val handler = handlerWith(maxAttempts = 1, meterRegistry = null) {
            Mono.just(ClientResponse.create(HttpStatus.OK).build())
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(HttpStatus.OK, exchange.response.statusCode)
    }

    private fun handlerWith(
        maxAttempts: Int = 1,
        meterRegistry: MeterRegistry? = null,
        exchange: ExchangeFunction
    ): ProxyHandler =
        ProxyHandler(
            WebClient.builder().exchangeFunction(exchange).build(),
            GatewayRouteSelector(
                GatewayRouteProperties(
                    applicationUrl = "http://application.internal",
                    batchUrl = "http://batch.internal",
                ),
            ),
            GatewayRetryProperties(maxAttempts = maxAttempts, backoff = Duration.ofMillis(10)),
            meterRegistry
        )

    private fun serverRequest(request: MockServerHttpRequest): ServerRequest {
        val exchange = MockServerWebExchange.from(request)
        return serverRequest(exchange.request)
    }

    private fun serverRequest(request: ServerHttpRequest): ServerRequest {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.method(request.method, request.uri).headers(request.headers),
        )
        return ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders())
    }

    private fun responseContext(): ServerResponse.Context {
        val strategies = HandlerStrategies.withDefaults()
        return object : ServerResponse.Context {
            override fun messageWriters() = strategies.messageWriters()
            override fun viewResolvers() = strategies.viewResolvers()
        }
    }
}
