package cc.midolog.gateway.proxy

import cc.midolog.gateway.config.GatewayRetryProperties
import cc.midolog.gateway.route.GatewayRouteProperties
import cc.midolog.gateway.route.GatewayRouteSelector
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
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
import reactor.core.publisher.Mono
import java.net.ConnectException
import java.time.Duration
import java.util.concurrent.TimeoutException

class ProxyHandlerTest {

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

    @ParameterizedTest
    @ValueSource(ints = [502, 503, 504])
    fun `Returns identical upstream status when max-attempts exhausted for idempotent requests`(status: Int) {
        var callCount = 0
        val responseMock = mock(ClientResponse::class.java)
        `when`(responseMock.statusCode()).thenReturn(HttpStatus.valueOf(status))
        `when`(responseMock.releaseBody()).thenReturn(Mono.empty())

        val handler = handlerWith(maxAttempts = 3) {
            callCount++
            Mono.just(responseMock)
        }
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val response = handler.proxy(serverRequest(exchange.request)).block()
        response!!.writeTo(exchange, responseContext()).block()

        assertEquals(3, callCount)
        assertEquals(HttpStatus.valueOf(status), exchange.response.statusCode)
        verify(responseMock, times(3)).releaseBody()
    }

    @ParameterizedTest
    @ValueSource(strings = ["POST", "PUT", "PATCH", "DELETE"])
    fun `Non-idempotent requests do not retry on 503 and return 502 according to policy`(methodName: String) {
        var callCount = 0
        val responseMock = mock(ClientResponse::class.java)
        `when`(responseMock.statusCode()).thenReturn(HttpStatus.SERVICE_UNAVAILABLE)
        `when`(responseMock.releaseBody()).thenReturn(Mono.empty())

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
        verify(responseMock, times(1)).releaseBody()
    }

    @ParameterizedTest
    @ValueSource(strings = ["GET", "HEAD", "OPTIONS"])
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
        val client = WebClient.builder().baseUrl("http://localhost:23456").filter { request, next -> reqCount++; next.exchange(request) }.build()
        val handler = ProxyHandler(
            client,
            GatewayRouteSelector(GatewayRouteProperties("http://localhost:23456", "http://localhost:23456")),
            GatewayRetryProperties(maxAttempts = 3, backoff = Duration.ofMillis(1))
        )
        
        // GET should retry
        val getExchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/retry"))
        val getResponse = handler.proxy(serverRequest(getExchange.request)).block()
        getResponse!!.writeTo(getExchange, responseContext()).block()
        assertEquals(HttpStatus.BAD_GATEWAY, getExchange.response.statusCode)
        assertEquals(3, reqCount)
        reqCount = 0

        // POST should not retry, just return 502
        val postExchange = MockServerWebExchange.from(MockServerHttpRequest.post("/api/retry"))
        val postResponse = handler.proxy(serverRequest(postExchange.request)).block()
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
