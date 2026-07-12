package cc.midolog.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.http.server.reactive.MockServerHttpRequest
import org.springframework.mock.web.server.MockServerWebExchange
import reactor.core.publisher.Mono
import java.util.Date

class JwtAuthFilterTest {

    private val validSecret = "0123456789abcdef0123456789abcdef-strong-random-secret"
    private val filter = JwtAuthFilter(validSecret)

    private fun validToken(): String {
        val key = Keys.hmacShaKeyFor(validSecret.toByteArray())
        return Jwts.builder()
            .subject("demo-user")
            .issuedAt(Date())
            .expiration(Date(System.currentTimeMillis() + 60_000))
            .signWith(key)
            .compact()
    }

    @Test
    fun `passes request with a valid Bearer token`() {
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/protected").header("Authorization", "Bearer ${validToken()}")
        )
        filter.filter(exchange) { Mono.empty() }.block()
        assertNotEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `returns 401 when Authorization header is missing`() {
        val exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/protected"))
        filter.filter(exchange) { Mono.empty() }.block()
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `returns 401 when Bearer token is tampered`() {
        val tampered = validToken() + "tampered"
        val exchange = MockServerWebExchange.from(
            MockServerHttpRequest.get("/api/protected").header("Authorization", "Bearer $tampered")
        )
        filter.filter(exchange) { Mono.empty() }.block()
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.response.statusCode)
    }

    @Test
    fun `fails fast when constructed with an invalid secret`() {
        assertThrows(IllegalStateException::class.java) {
            JwtAuthFilter("too-short-secret")
        }
    }
}
