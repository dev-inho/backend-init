package cc.midolog.gateway.visibility

/**
 * 메모리에 버퍼링된 최근 요청 가시성 목록을 제공하는 내부 관리용 핸들러.
 *
 * `gateway.request-visibility.enabled=true` 조건에서만 빈으로 활성화된다.
 *
 * 보안 및 인증 정책:
 * 매핑 경로인 `/internal/gateway/requests`는 standalone/remote 모드의 경우
 * [cc.midolog.gateway.filter.JwtAuthFilter]가 인증을 검증하며,
 * embedded 모드의 경우 호스트 application의 SecurityConfig가 인증을 담당한다.
 * 게이트웨이 내부 관측 데이터(엔드포인트 호출 패턴, 상태 코드 등)가 외부에 무단
 * 노출되지 않도록 인가된 요청자에게만 조회를 허용하기 위함이다.
 *
 * 민감정보 보호:
 * 요청/응답 본문, 쿼리스트링, 인증 헤더 등 PII나 보안상 민감한 자격증명은 저장 및 반환하지
 * 않고 HTTP 메서드, 경로, 상태 코드, 소요 시간 등 최소한의 메타데이터만 반환한다.
 */
class RequestVisibilityHandler(
    private val eventStore: RequestEventStore,
) {
    fun recent(request: org.springframework.web.reactive.function.server.ServerRequest): reactor.core.publisher.Mono<org.springframework.web.reactive.function.server.ServerResponse> =
        org.springframework.web.reactive.function.server.ServerResponse.ok().bodyValue(eventStore.recent())
}
