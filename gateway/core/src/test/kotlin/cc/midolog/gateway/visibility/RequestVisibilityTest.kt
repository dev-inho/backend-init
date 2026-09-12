package cc.midolog.gateway.visibility

import cc.midolog.gateway.filter.JwtAuthFilter
import cc.midolog.gateway.config.GatewayClockConfig
import cc.midolog.logging.LoggingMdc
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.runner.ApplicationContextRunner
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono

class RequestVisibilityTest {

    private val clock = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC)



    @Test
    fun `store keeps bounded recent events`() {
        val store = RequestEventStore(RequestVisibilityProperties(capacity = 2))

        store.record(event(path = "/api/one"))
        store.record(event(path = "/api/two"))
        store.record(event(path = "/api/three"))

        assertEquals(listOf("/api/three", "/api/two"), store.recent().map { it.path })
    }

    @Test
    fun `filter records minimal event data after response completion`() {
        val store = RequestEventStore(RequestVisibilityProperties(capacity = 10))
        val filter = RequestVisibilityFilter(store, clock)
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/users?token=secret")
                .header(LoggingMdc.REQUEST_ID, "request-1000")
                .header(HttpHeaders.AUTHORIZATION, "Bearer secret")
                .build(),
        )

        filter.filter(exchange) {
            it.response.statusCode = HttpStatus.ACCEPTED
            Mono.empty()
        }.block()

        val recorded = store.recent().single()
        assertEquals("GET", recorded.method)
        assertEquals("/api/users", recorded.path)
        assertEquals(202, recorded.status)
        assertEquals("request-1000", recorded.requestId)
        assertEquals(Instant.parse("2026-07-17T12:00:00Z"), recorded.timestamp)
        assertFalse(recorded.toString().contains("secret"))
    }

    @Test
    fun `controller returns recent events without raw body or headers`() {
        val store = RequestEventStore(RequestVisibilityProperties(capacity = 10))
        store.record(event(path = "/api/users"))
        val controller = RequestVisibilityController(store)

        val response = controller.recent()

        assertEquals(1, response.size)
        assertEquals("/api/users", response.single().path)
        assertNull(response.single().requestId)
    }

    @Test
    fun `internal gateway requests require a valid jwt`() {
        val secret = "0123456789abcdef0123456789abcdef-strong-random-secret"
        val filter = JwtAuthFilter(secret)
        val missingTokenExchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/internal/gateway/requests"),
        )

        filter.filter(missingTokenExchange) { Mono.empty() }.block()

        assertEquals(HttpStatus.UNAUTHORIZED, missingTokenExchange.response.statusCode)

        val authorizedExchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/internal/gateway/requests")
                .header(HttpHeaders.AUTHORIZATION, "Bearer ${validToken(secret)}"),
        )

        filter.filter(authorizedExchange) { Mono.empty() }.block()

        assertNotEquals(HttpStatus.UNAUTHORIZED, authorizedExchange.response.statusCode)
    }

    private fun event(path: String): RequestVisibilityEvent =
        RequestVisibilityEvent(
            method = "GET",
            path = path,
            status = 200,
            requestId = null,
            timestamp = Instant.parse("2026-07-17T12:00:00Z"),
            durationMs = 5,
        )

    private fun validToken(secret: String): String {
        val key = Keys.hmacShaKeyFor(secret.toByteArray())
        return Jwts.builder()
            .subject("demo-user")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(key)
            .compact()
    }


}
