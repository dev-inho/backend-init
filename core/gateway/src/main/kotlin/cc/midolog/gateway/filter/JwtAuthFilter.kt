package cc.midolog.gateway.filter

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import javax.crypto.SecretKey

/**
 * 게이트웨이 JWT 검증 필터.
 * - /api/auth, /actuator, /batch 하위: 통과(인증 불필요)
 * - 그 외 /api 하위: Authorization Bearer 토큰 필수, 검증 실패 시 401
 */
@Component
@Order(1)
class JwtAuthFilter(
    @Value("\${jwt.secret}") secret: String,
) : WebFilter {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        if (path.startsWith("/api/auth/") || path.startsWith("/actuator/") || path.startsWith("/batch/")) {
            return chain.filter(exchange)
        }
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange)
        }
        val header = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange)
        }
        return try {
            Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(header.removePrefix("Bearer ").trim())
            chain.filter(exchange)
        } catch (e: Exception) {
            unauthorized(exchange)
        }
    }

    private fun unauthorized(exchange: ServerWebExchange): Mono<Void> {
        exchange.response.statusCode = HttpStatus.UNAUTHORIZED
        return exchange.response.setComplete()
    }
}
