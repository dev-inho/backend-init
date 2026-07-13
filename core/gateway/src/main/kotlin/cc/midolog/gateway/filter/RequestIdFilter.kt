package cc.midolog.gateway.filter

import cc.midolog.logging.LoggingMdc
import org.slf4j.MDC
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * 트랜잭션 ID(X-Request-Id) 필터.
 * - 요청에 X-Request-Id가 있으면 유지, 없으면 UUID 생성
 * - 다운스트림 요청 헤더와 응답 헤더에 모두 전파
 * - reactor Context에 바인딩하여 로깅(MDC) 연동 기반 제공
 */
@Component
@Order(0)
class RequestIdFilter : WebFilter {

    companion object {
        const val HEADER = LoggingMdc.REQUEST_ID
        const val MAX_LENGTH = 128

        /** 허용 문자: 영숫자, 하이픈, 언더스코어 (로그 인젝션 방지). */
        private val VALID_PATTERN = Regex("^[A-Za-z0-9_-]{1,$MAX_LENGTH}$")
    }

    /**
     * 외부에서 전달된 X-Request-Id가 허용 형식이면 그대로 사용하고,
     * 없거나 형식 위반(길이 초과·비허용 문자)이면 새 UUID를 생성한다.
     */
    private fun resolveRequestId(raw: String?): String =
        if (raw != null && VALID_PATTERN.matches(raw)) raw else UUID.randomUUID().toString()

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val requestId = resolveRequestId(exchange.request.headers.getFirst(HEADER))

        val mutated = exchange.mutate()
            .request(exchange.request.mutate().header(HEADER, requestId).build())
            .build()

        mutated.response.headers.set(HEADER, requestId)

        return chain.filter(mutated)
            .contextWrite { ctx -> ctx.put(HEADER, requestId) }
            .doFirst { MDC.put(HEADER, requestId) }
            .doFinally { MDC.remove(HEADER) }
    }
}
