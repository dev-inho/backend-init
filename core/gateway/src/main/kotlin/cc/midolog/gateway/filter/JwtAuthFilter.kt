package cc.midolog.gateway.filter

import cc.midolog.jwt.JwtCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 게이트웨이 JWT 검증 필터.
 * - /api/auth, /actuator, /batch 하위: 통과(인증 불필요)
 * - 그 외 /api 및 /internal/gateway 하위: Authorization Bearer 토큰 필수, 검증 실패 시 401
 *
 * 실제 토큰 파싱 동작은 [JwtCodec]에 위임한다.
 */
@Component
@Order(1)
class JwtAuthFilter(
    @Value("\${jwt.secret}") secret: String,
) : WebFilter {
    private val codec = JwtCodec(secret)

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val path = exchange.request.path.value()
        if (path.startsWith("/api/auth/") || path.startsWith("/actuator/") || path.startsWith("/batch/")) {
            return chain.filter(exchange)
        }
        if (!path.startsWith("/api/") && !path.startsWith("/internal/gateway/")) {
            return chain.filter(exchange)
        }
        val header = exchange.request.headers.getFirst(HttpHeaders.AUTHORIZATION)
        if (header == null || !header.startsWith("Bearer ")) {
            return unauthorized(exchange)
        }
        return try {
            codec.parse(header.removePrefix("Bearer ").trim())
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
