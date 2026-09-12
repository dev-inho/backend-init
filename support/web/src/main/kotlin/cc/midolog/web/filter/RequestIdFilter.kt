package cc.midolog.web.filter

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
 * HTTP 요청 추적을 위한 트랜잭션 식별자(`X-Request-Id`)를 부여하고 전파하는 WebFilter.
 *
 * Spring 컴포넌트 스캔을 통해 gateway와 application 양쪽 모듈에 자동 등록된다.
 * 필터 순서는 `@Order(0)`으로, 최외곽 로깅 필터([HttpLoggingFilter], @Order(-2)) 뒤에서 동작하며
 * 인증 필터(`JwtAuthFilter`, @Order(1)) 등 다운스트림 컴포넌트 실행 전에 요청 ID를 확정한다.
 */
@Component
@Order(0)
class RequestIdFilter : WebFilter {

    companion object {
        /** 요청/응답 헤더 및 Reactor Context, MDC 키로 사용할 표준 식별자 이름([LoggingMdc.REQUEST_ID]). */
        const val HEADER = LoggingMdc.REQUEST_ID

        /** 외부에서 전달된 요청 ID의 최대 허용 길이(128자). 메모리 낭비 및 로그 저장소 오염을 방지한다. */
        const val MAX_LENGTH = 128

        /** 외부 입력값 검증용 정규식(영숫자, 하이픈, 언더스코어). CRLF 로그 인젝션 및 특수문자 오염을 차단한다. */
        private val VALID_PATTERN = Regex("^[A-Za-z0-9_-]{1,$MAX_LENGTH}$")
    }

    /**
     * 외부에서 전달된 [HEADER] 값을 검증해 유효하면 그대로 채택하고, 없거나 패턴 위반 시 신규 UUID를 발급한다.
     */
    private fun resolveRequestId(raw: String?): String =
        if (raw != null && VALID_PATTERN.matches(raw)) raw else UUID.randomUUID().toString()

    /**
     * 요청 헤더와 응답 헤더에 요청 ID를 주입하고, Reactor Context와 MDC에 동기화한다.
     *
     * 체인 전체에서 식별자를 참조할 수 있도록 `contextWrite`로 Reactor Context에 바인딩한다.
     * 또한 체인 진입 직전 `doFirst`에서 MDC에 put하고, 체인 완료/에러/취소 시점 `doFinally`에서
     * MDC를 remove하여 스레드 풀 재사용 시의 MDC 오염을 방지한다.
     */
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
