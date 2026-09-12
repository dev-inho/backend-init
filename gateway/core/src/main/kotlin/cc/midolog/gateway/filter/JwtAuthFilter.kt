package cc.midolog.gateway.filter

import cc.midolog.jwt.JwtCodec
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

/**
 * 인입 요청의 Authorization Bearer 토큰 서명과 유효성을 검증하는 보안 필터.
 *
 * 필터 체인 순서 계약:
 * -2 HttpLoggingFilter(support:web) → -1 AuthTokenRateLimitFilter → 0 RequestIdFilter(support:web) → 1 JwtAuthFilter → 100 RequestVisibilityFilter
 *
 * 앞뒤 순서와 위치 이유:
 * 앞에는 cc.midolog.web.filter.HttpLoggingFilter(@Order(-2)), [AuthTokenRateLimitFilter](@Order(-1)),
 * cc.midolog.web.filter.RequestIdFilter(@Order(0))가 먼저 실행된다. 특히 RequestIdFilter(0)가
 * X-Request-Id를 부여하고 Reactor Context/MDC를 구성한 뒤 실행되므로 인증 거절(401) 로그와
 * 응답 헤더에도 일관된 트랜잭션 ID가 남는다. 뒤에는 cc.midolog.gateway.visibility.RequestVisibilityFilter(@Order(100))가
 * 위치하여, 이 필터가 401로 단축 응답하더라도 완료 시점에 401 상태 코드까지 빠짐없이 가시성 저장소에 기록된다.
 * 토큰 발급 보호와 요청 식별이 끝난 직후 비즈니스 라우팅 직전에 미인증 요청을 조기 차단하기 위해 이 자리에 둔다.
 *
 * 토큰 검증은 공통 모듈의 [JwtCodec]에 위임한다. 게이트웨이와 백엔드 애플리케이션(core/application)이
 * 동일한 시크릿과 파싱 규칙을 공유하며, jjwt 라이브러리 의존성을 support:jwt 모듈에 격리하기 위함이다.
 */
@Order(1)
class JwtAuthFilter(
    @Value("\${jwt.secret}") secret: String,
) : WebFilter {
    private val codec = JwtCodec(secret)

    /**
     * 경로 정책에 따라 토큰을 검증하고, 유효한 경우 다음 체인으로 전달한다.
     *
     * 경로 정책:
     * - 통과 경로(/api/auth/, /actuator/, /batch/): 로그인·토큰 발급 등 공개 엔드포인트(/api/auth/),
     *   인프라 헬스체크 및 메트릭(/actuator/), 사내망/스케줄러에서 관리되는 배치 작업(/batch/)은 인증 없이 통과시킨다.
     * - 검증 경로(/api/, /internal/gateway/): 일반 비즈니스 API(/api/)와 게이트웨이 내부 관측 데이터
     *   조회 엔드포인트(/internal/gateway/)는 Bearer 토큰 검증을 필수 적용한다.
     * - 기타 경로: 라우터에서 처리되지 않는 경로는 검증을 건너뛰고 체인으로 넘겨 404 등 표준 처리로 위임한다.
     *
     * 401 응답 본문 정책:
     * 헤더 누락, 포맷 불일치, 서명 만료 등 검증 실패 시 응답 본문 없이 401(UNAUTHORIZED) 상태 코드만
     * 반환하고 종료한다. 공격자에게 구체적인 실패 원인이나 내부 스택트레이스를 노출하지 않기 위함이다.
     */
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
