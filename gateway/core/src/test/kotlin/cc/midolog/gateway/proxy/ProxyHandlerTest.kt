package cc.midolog.gateway.proxy

import cc.midolog.gateway.circuitbreaker.CircuitBreakerRegistry
import cc.midolog.gateway.circuitbreaker.CircuitBreakerState
import cc.midolog.gateway.config.GatewayCircuitBreakerProperties
import cc.midolog.gateway.config.GatewayRouteProperties
import cc.midolog.gateway.config.GatewayRetryProperties
import cc.midolog.gateway.route.GatewayRouteSelector
import cc.midolog.logging.LoggingMdc
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.micrometer.core.instrument.MeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
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
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.TimeoutException
import java.net.ConnectException

class ProxyHandlerTest {

@Test
    fun `Backoff delays exponentially`() {
        var callCount = 0
        val handler = handlerWith(maxAttempts = 3, backoff = java.time.Duration.ofMillis(100)) {
            callCount++
            Mono.just(ClientResponse.create(HttpStatus.SERVICE_UNAVAILABLE).build())
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))

        reactor.test.StepVerifier.withVirtualTime { handler.proxy(serverRequest(exchange.request)) }
            .then { assertEquals(1, callCount) }
            .thenAwait(java.time.Duration.ofMillis(99))
            .then { assertEquals(1, callCount) }
            .thenAwait(java.time.Duration.ofMillis(1))
            .then { assertEquals(2, callCount) }
            .thenAwait(java.time.Duration.ofMillis(199))
            .then { assertEquals(2, callCount) }
            .thenAwait(java.time.Duration.ofMillis(1))
            .then { assertEquals(3, callCount) }
            .expectNextCount(1)
            .verifyComplete()
    }




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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = [502, 503, 504])
    fun `Returns identical upstream status when max-attempts exhausted for idempotent requests`(status: Int) {
        var callCount = 0
        val responseMock = org.mockito.Mockito.mock(ClientResponse::class.java)
        org.mockito.Mockito.`when`(responseMock.statusCode()).thenReturn(HttpStatus.valueOf(status))
        org.mockito.Mockito.`when`(responseMock.releaseBody()).thenReturn(Mono.empty())

        val handler = handlerWith(maxAttempts = 3) {
            callCount++
            Mono.just(responseMock)
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(3, callCount)
        assertEquals(HttpStatus.valueOf(status), exchange.response.statusCode)
        org.mockito.Mockito.verify(responseMock, org.mockito.Mockito.times(6)).releaseBody()
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = ["POST", "PUT", "PATCH", "DELETE"])
    fun `Non-idempotent requests do not retry on 503 and return 502 according to policy`(methodName: String) {
        var callCount = 0
        val responseMock = org.mockito.Mockito.mock(ClientResponse::class.java)
        org.mockito.Mockito.`when`(responseMock.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE)
        org.mockito.Mockito.`when`(responseMock.releaseBody()).thenReturn(Mono.empty())

        val handler = handlerWith(maxAttempts = 3) {
            callCount++
            Mono.just(responseMock)
        }
        val request = MockServerHttpRequest.method(HttpMethod.valueOf(methodName), "/api/retry").build()
        val exchange = MockServerWebExchange.from(request)
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(1, callCount)
        assertEquals(HttpStatus.BAD_GATEWAY, exchange.response.statusCode)
        org.mockito.Mockito.verify(responseMock, org.mockito.Mockito.times(2)).releaseBody()
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = ["GET", "HEAD", "OPTIONS"])
    fun `Idempotent requests retry on TimeoutException`(methodName: String) {
        var callCount = 0
        val handler = handlerWith(maxAttempts = 2) {
            callCount++
            Mono.error(TimeoutException("Timeout"))
        }
        val request = MockServerHttpRequest.method(HttpMethod.valueOf(methodName), "/api/retry").build()
        val exchange = MockServerWebExchange.from(request)
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(2, callCount)
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exchange.response.statusCode)
    }

    @Test
    fun `Actual unavailable localhost upstream retries GET and not POST`() {
        var reqCount = 0
        val exchangeFunction = ExchangeFunction { _ ->
            reqCount++
            Mono.error(java.net.ConnectException("Connection refused"))
        }
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()
        val handler = ProxyHandler(
            client,
            GatewayRouteSelector(cc.midolog.gateway.config.GatewayRouteProperties("http://application-a.internal", batchUrl = "http://batch.internal"), client, java.time.Clock.systemUTC(), null),
            GatewayRetryProperties(maxAttempts = 3, backoff = java.time.Duration.ofMillis(1))
        )

        // GET should retry
        val getExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val getResponse = handler.proxy(serverRequest(getExchange.request)).block()
        getResponse!!.writeTo(getExchange, responseContext()).block()
        assertEquals(HttpStatus.BAD_GATEWAY, getExchange.response.statusCode)
        assertEquals(3, reqCount)

        reqCount = 0

        // POST should not retry, just return 502
        val postHandler = ProxyHandler(
            client,
            GatewayRouteSelector(cc.midolog.gateway.config.GatewayRouteProperties("http://application-a.internal", batchUrl = "http://batch.internal"), client, java.time.Clock.systemUTC(), null),
            GatewayRetryProperties(maxAttempts = 3, backoff = java.time.Duration.ofMillis(1))
        )
        val postExchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/retry"))
        val postResponse = postHandler.proxy(serverRequest(postExchange.request)).block()
        postResponse!!.writeTo(postExchange, responseContext()).block()
        assertEquals(HttpStatus.BAD_GATEWAY, postExchange.response.statusCode)
        assertEquals(1, reqCount)
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
    fun `MeterRegistry failure paths`() {
        val registry = SimpleMeterRegistry()
        var callCount = 0
        val handler = handlerWith(maxAttempts = 2, meterRegistry = registry) {
            callCount++
            Mono.error(ConnectException("refused"))
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        val requests = registry.get("gateway.proxy.requests").counter()
        assertEquals(1.0, requests.count())
        assertEquals("http://application.internal", requests.id.getTag("target"))
        assertEquals("502", requests.id.getTag("status"))
        assertEquals("true", requests.id.getTag("retried"))
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

        // Failure without registry
        val failHandler = handlerWith(maxAttempts = 2, meterRegistry = null) {
            Mono.error(TimeoutException("timeout"))
        }
        val failExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val failResponse = failHandler.proxy(serverRequest(failExchange.request)).block()
        failResponse!!.writeTo(failExchange, responseContext()).block()

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, failExchange.response.statusCode)
    }

    private fun handlerWith(
        maxAttempts: Int = 1,
        meterRegistry: MeterRegistry? = null,
        backoff: java.time.Duration = java.time.Duration.ofMillis(10),
        exchange: ExchangeFunction
    ): ProxyHandler =
        ProxyHandler(
            WebClient.builder().exchangeFunction(exchange).build(),
            GatewayRouteSelector(
                GatewayRouteProperties(
                    applicationUrl = "http://application.internal",
                    batchUrl = "http://batch.internal",
                ),
                WebClient.builder().exchangeFunction(exchange).build(),
                java.time.Clock.systemUTC(),
                meterRegistry
            ),
            GatewayRetryProperties(maxAttempts = maxAttempts, backoff = backoff),
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

    @Test
    fun `Connection exception marks target as unhealthy immediately`() {
        val exchangeFunction = ExchangeFunction { _ ->
            Mono.error(java.net.ConnectException("Connection refused"))
        }
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()

        val properties = GatewayRouteProperties(
            applicationUrls = listOf("http://application-a.internal", "http://application-b.internal"),
            batchUrl = "http://batch.internal"
        )
        val selector = GatewayRouteSelector(properties, client, java.time.Clock.systemUTC(), null)

        val handler = ProxyHandler(
            client,
            selector,
            GatewayRetryProperties(maxAttempts = 1, backoff = java.time.Duration.ofMillis(1))
        )

        val getExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val getResponse = handler.proxy(serverRequest(getExchange.request)).block()
        getResponse!!.writeTo(getExchange, responseContext()).block()

        assertEquals(HttpStatus.BAD_GATEWAY, getExchange.response.statusCode)

        // application-a should be marked unhealthy. Next request should route to application-b.
        assertEquals("http://application-b.internal", selector.selectTarget("/api/retry"))
        assertEquals("http://application-b.internal", selector.selectTarget("/api/retry"))
    }

    @Test
    fun `Timeout and 503 do not mark target as unhealthy`() {
        var status = HttpStatus.SERVICE_UNAVAILABLE
        var isTimeout = false
        val exchangeFunction = ExchangeFunction { _ ->
            if (isTimeout) Mono.error(java.util.concurrent.TimeoutException("Timeout"))
            else Mono.just(ClientResponse.create(status).build())
        }
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()

        val properties = GatewayRouteProperties(
            applicationUrls = listOf("http://application-a.internal", "http://application-b.internal"),
            batchUrl = "http://batch.internal"
        )
        val selector = GatewayRouteSelector(properties, client, java.time.Clock.systemUTC(), null)

        val handler = ProxyHandler(
            client,
            selector,
            GatewayRetryProperties(maxAttempts = 1, backoff = java.time.Duration.ofMillis(1))
        )

        // 503 test
        val exchange503 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        handler.proxy(serverRequest(exchange503.request)).block()!!.writeTo(exchange503, responseContext()).block()

        // Timeout test
        isTimeout = true
        val exchangeTimeout = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        handler.proxy(serverRequest(exchangeTimeout.request)).block()!!.writeTo(exchangeTimeout, responseContext()).block()

        // Both targets should still be round-robining because they are not unhealthy
        val t1 = selector.selectTarget("/api/retry")
        val t2 = selector.selectTarget("/api/retry")
        assertEquals(setOf("http://application-a.internal", "http://application-b.internal"), setOf(t1, t2))
    }

    @Test
    fun `Batch target connection failure does not change application target states`() {
        val exchangeFunction = ExchangeFunction { _ ->
            Mono.error(java.net.ConnectException("Connection refused"))
        }
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()

        val properties = GatewayRouteProperties(
            applicationUrls = listOf("http://application-a.internal", "http://application-b.internal"),
            batchUrl = "http://batch.internal"
        )
        val selector = GatewayRouteSelector(properties, client, java.time.Clock.systemUTC(), null)

        val handler = ProxyHandler(
            client,
            selector,
            GatewayRetryProperties(maxAttempts = 1, backoff = java.time.Duration.ofMillis(1))
        )

        val batchExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/batch/jobs"))
        handler.proxy(serverRequest(batchExchange.request)).block()!!.writeTo(batchExchange, responseContext()).block()

        // Batch target is stateless in GatewayRouteSelector, application targets remain healthy
        val t1 = selector.selectTarget("/api/retry")
        val t2 = selector.selectTarget("/api/retry")
        assertEquals(setOf("http://application-a.internal", "http://application-b.internal"), setOf(t1, t2))
    }

    private class MutableClock(private var current: Instant = Instant.parse("2026-09-19T00:00:00Z")) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = current
        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    @Test
    fun `Circuit breaker trips to OPEN after threshold failures and short-circuits subsequent requests with 503`() {
        var callCount = 0
        val clock = MutableClock()
        val breakerProps = GatewayCircuitBreakerProperties(failureThreshold = 2, openDuration = Duration.ofSeconds(5))
        val registry = CircuitBreakerRegistry(breakerProps, clock)

        val exchangeFunction = ExchangeFunction { _ ->
            callCount++
            Mono.error(ConnectException("Connection refused"))
        }
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()
        val selector = GatewayRouteSelector(
            GatewayRouteProperties(applicationUrl = "http://application.internal", batchUrl = "http://batch.internal"),
            client,
            clock,
            null
        )
        val handler = ProxyHandler(
            client,
            selector,
            GatewayRetryProperties(maxAttempts = 1),
            null,
            registry
        )

        // 1st request -> fail -> 502, callCount=1
        val ex1 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"))
        val resp1 = handler.proxy(serverRequest(ex1.request)).block()!!
        resp1.writeTo(ex1, responseContext()).block()
        assertEquals(HttpStatus.BAD_GATEWAY, ex1.response.statusCode)
        assertEquals(1, callCount)

        // 2nd request -> fail -> 502, callCount=2 -> breaker trips to OPEN
        val ex2 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"))
        val resp2 = handler.proxy(serverRequest(ex2.request)).block()!!
        resp2.writeTo(ex2, responseContext()).block()
        assertEquals(HttpStatus.BAD_GATEWAY, ex2.response.statusCode)
        assertEquals(2, callCount)
        assertEquals(CircuitBreakerState.OPEN, registry.getOrCreate("http://application.internal").currentState())

        // 3rd request -> circuit is OPEN -> short-circuit to 503 without calling downstream!
        val ex3 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/test"))
        val resp3 = handler.proxy(serverRequest(ex3.request)).block()!!
        resp3.writeTo(ex3, responseContext()).block()
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex3.response.statusCode)
        assertEquals(2, callCount, "Downstream must not be invoked when circuit is OPEN")
    }

    @Test
    fun `Retry does not bypass open circuit and stops on breaker rejection with 503`() {
        var callCount = 0
        val clock = MutableClock()
        val breakerProps = GatewayCircuitBreakerProperties(failureThreshold = 2, openDuration = Duration.ofSeconds(5))
        val registry = CircuitBreakerRegistry(breakerProps, clock)

        val exchangeFunction = ExchangeFunction { _ ->
            callCount++
            Mono.error(ConnectException("Downstream unavailable"))
        }
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()
        val selector = GatewayRouteSelector(
            GatewayRouteProperties(applicationUrl = "http://application.internal", batchUrl = "http://batch.internal"),
            client,
            clock,
            null
        )
        val handler = ProxyHandler(
            client,
            selector,
            GatewayRetryProperties(maxAttempts = 3, backoff = Duration.ofMillis(1)),
            null,
            registry
        )

        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry-breaker"))
        val response = handler.proxy(serverRequest(exchange.request)).block()!!
        response.writeTo(exchange, responseContext()).block()

        // Attempt 1: fail (callCount=1, streak=1)
        // Attempt 2 (retry): fail (callCount=2, streak=2 -> trips to OPEN)
        // Attempt 3 (retry): breaker rejects call immediately with 503, callCount remains 2!
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.response.statusCode)
        assertEquals(2, callCount)
        assertEquals(CircuitBreakerState.OPEN, registry.getOrCreate("http://application.internal").currentState())
    }

    @Test
    fun `Probe in HALF_OPEN recovers circuit to CLOSED on successful response`() {
        var callCount = 0
        val clock = MutableClock()
        val breakerProps = GatewayCircuitBreakerProperties(failureThreshold = 1, openDuration = Duration.ofSeconds(5))
        val registry = CircuitBreakerRegistry(breakerProps, clock)

        var returnSuccess = false
        val exchangeFunction = ExchangeFunction { _ ->
            callCount++
            if (returnSuccess) {
                Mono.just(ClientResponse.create(HttpStatus.OK).body("success").build())
            } else {
                Mono.error(ConnectException("downstream error"))
            }
        }
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()
        val selector = GatewayRouteSelector(
            GatewayRouteProperties(applicationUrl = "http://application.internal", batchUrl = "http://batch.internal"),
            client,
            clock,
            null
        )
        val handler = ProxyHandler(client, selector, GatewayRetryProperties(maxAttempts = 1), null, registry)

        // 1st request trips breaker
        val ex1 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/probe"))
        handler.proxy(serverRequest(ex1.request)).block()!!.writeTo(ex1, responseContext()).block()
        assertEquals(CircuitBreakerState.OPEN, registry.getOrCreate("http://application.internal").currentState())

        // Advance past openDuration -> HALF_OPEN
        clock.advance(Duration.ofSeconds(6))
        assertEquals(CircuitBreakerState.HALF_OPEN, registry.getOrCreate("http://application.internal").currentState())

        // Downstream now healthy
        returnSuccess = true
        val ex2 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/probe"))
        handler.proxy(serverRequest(ex2.request)).block()!!.writeTo(ex2, responseContext()).block()
        assertEquals(HttpStatus.OK, ex2.response.statusCode)
        assertEquals(CircuitBreakerState.CLOSED, registry.getOrCreate("http://application.internal").currentState())
        assertEquals(0, registry.getOrCreate("http://application.internal").failureStreak())

        // Subsequent requests continue to succeed
        val ex3 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/probe"))
        handler.proxy(serverRequest(ex3.request)).block()!!.writeTo(ex3, responseContext()).block()
        assertEquals(HttpStatus.OK, ex3.response.statusCode)
        assertEquals(CircuitBreakerState.CLOSED, registry.getOrCreate("http://application.internal").currentState())
    }

    @Test
    fun `Probe in HALF_OPEN transitions back to OPEN on downstream error and rejects subsequent requests`() {
        var callCount = 0
        val clock = MutableClock()
        val breakerProps = GatewayCircuitBreakerProperties(failureThreshold = 1, openDuration = Duration.ofSeconds(5))
        val registry = CircuitBreakerRegistry(breakerProps, clock)

        val exchangeFunction = ExchangeFunction { _ ->
            callCount++
            Mono.error(ConnectException("downstream error"))
        }
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()
        val selector = GatewayRouteSelector(
            GatewayRouteProperties(applicationUrl = "http://application.internal", batchUrl = "http://batch.internal"),
            client,
            clock,
            null
        )
        val handler = ProxyHandler(client, selector, GatewayRetryProperties(maxAttempts = 1), null, registry)

        // Trip breaker to OPEN
        val ex1 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/probe-fail"))
        handler.proxy(serverRequest(ex1.request)).block()!!.writeTo(ex1, responseContext()).block()
        assertEquals(CircuitBreakerState.OPEN, registry.getOrCreate("http://application.internal").currentState())
        assertEquals(1, callCount)

        // Advance to HALF_OPEN
        clock.advance(Duration.ofSeconds(6))
        assertEquals(CircuitBreakerState.HALF_OPEN, registry.getOrCreate("http://application.internal").currentState())

        // Probe fails -> transitions back to OPEN
        val ex2 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/probe-fail"))
        handler.proxy(serverRequest(ex2.request)).block()!!.writeTo(ex2, responseContext()).block()
        assertEquals(HttpStatus.BAD_GATEWAY, ex2.response.statusCode)
        assertEquals(2, callCount)
        assertEquals(CircuitBreakerState.OPEN, registry.getOrCreate("http://application.internal").currentState())

        // Immediate next call is short-circuited (503) without calling downstream
        val ex3 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/probe-fail"))
        handler.proxy(serverRequest(ex3.request)).block()!!.writeTo(ex3, responseContext()).block()
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex3.response.statusCode)
        assertEquals(2, callCount)
    }

    @Test
    fun `Concurrency limit in CLOSED rejects excess requests with 503`() {
        val clock = MutableClock()
        val breakerProps = GatewayCircuitBreakerProperties(maxConcurrentCalls = 1)
        val registry = CircuitBreakerRegistry(breakerProps, clock)

        val blocker = reactor.core.publisher.Sinks.empty<Void>()
        val exchangeFunction = ExchangeFunction { _ ->
            blocker.asMono().then(Mono.just(ClientResponse.create(HttpStatus.OK).body("delayed").build()))
        }
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()
        val selector = GatewayRouteSelector(
            GatewayRouteProperties(applicationUrl = "http://application.internal", batchUrl = "http://batch.internal"),
            client,
            clock,
            null
        )
        val handler = ProxyHandler(client, selector, GatewayRetryProperties(maxAttempts = 1), null, registry)

        // 1st request starts and holds the permit
        val ex1 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/concurrent"))
        val sub1 = handler.proxy(serverRequest(ex1.request)).subscribe()

        val breaker = registry.getOrCreate("http://application.internal")
        assertEquals(1, breaker.inFlightCount())

        // 2nd concurrent request exceeds limit -> 503
        val ex2 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/concurrent"))
        val resp2 = handler.proxy(serverRequest(ex2.request)).block()!!
        resp2.writeTo(ex2, responseContext()).block()
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex2.response.statusCode)

        // Finish 1st request
        blocker.tryEmitEmpty()
        sub1.dispose()

        assertEquals(0, breaker.inFlightCount())
    }

    @Test
    fun `Cancellation releases permit cleanly in ProxyHandler chain`() {
        val clock = MutableClock()
        val breakerProps = GatewayCircuitBreakerProperties(maxConcurrentCalls = 1)
        val registry = CircuitBreakerRegistry(breakerProps, clock)

        val exchangeFunction = ExchangeFunction { _ ->
            Mono.never<ClientResponse>()
        }
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()
        val selector = GatewayRouteSelector(
            GatewayRouteProperties(applicationUrl = "http://application.internal", batchUrl = "http://batch.internal"),
            client,
            clock,
            null
        )
        val handler = ProxyHandler(client, selector, GatewayRetryProperties(maxAttempts = 1), null, registry)

        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/cancel"))
        val sub = handler.proxy(serverRequest(exchange.request)).subscribe()

        val breaker = registry.getOrCreate("http://application.internal")
        assertEquals(1, breaker.inFlightCount())

        // Cancel
        sub.dispose()

        assertEquals(0, breaker.inFlightCount())
    }

    @Test
    fun `Target isolation preserves independent circuit breaker states across targets`() {
        val clock = MutableClock()
        val breakerProps = GatewayCircuitBreakerProperties(failureThreshold = 1)
        val registry = CircuitBreakerRegistry(breakerProps, clock)

        val exchangeFunction = ExchangeFunction { request ->
            if (request.url().toString().startsWith("http://application-a.internal")) {
                Mono.error(ConnectException("Target A down"))
            } else {
                Mono.just(ClientResponse.create(HttpStatus.OK).body("Target B ok").build())
            }
        }
        val client = WebClient.builder().exchangeFunction(exchangeFunction).build()
        val selector = GatewayRouteSelector(
            GatewayRouteProperties(
                applicationUrls = listOf("http://application-a.internal", "http://application-b.internal"),
                batchUrl = "http://batch.internal"
            ),
            client,
            clock,
            null
        )
        val handler = ProxyHandler(client, selector, GatewayRetryProperties(maxAttempts = 1), null, registry)

        // 1st request goes to application-a -> fails -> trips target A breaker & marks unhealthy in selector
        val ex1 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/target"))
        val resp1 = handler.proxy(serverRequest(ex1.request)).block()!!
        resp1.writeTo(ex1, responseContext()).block()
        assertEquals(HttpStatus.BAD_GATEWAY, ex1.response.statusCode)

        val breakerA = registry.getOrCreate("http://application-a.internal")
        val breakerB = registry.getOrCreate("http://application-b.internal")
        assertEquals(CircuitBreakerState.OPEN, breakerA.currentState())
        assertEquals(CircuitBreakerState.CLOSED, breakerB.currentState())

        // Next request is routed to application-b -> succeeds (200 OK)
        val ex2 = MockServerWebExchange.from(MockServerHttpRequest.get("/api/target"))
        val resp2 = handler.proxy(serverRequest(ex2.request)).block()!!
        resp2.writeTo(ex2, responseContext()).block()
        assertEquals(HttpStatus.OK, ex2.response.statusCode)
        assertEquals(CircuitBreakerState.CLOSED, breakerB.currentState())
    }
}
