package cc.midolog.gateway.visibility

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

/**
 * 메모리에 버퍼링된 최근 요청 가시성 목록을 제공하는 내부 관리용 REST 컨트롤러.
 *
 * `gateway.request-visibility.enabled=true` 조건에서만 빈으로 활성화된다.
 *
 * 보안 및 인증 정책:
 * 매핑 경로인 `/internal/gateway/requests`는 [cc.midolog.gateway.filter.JwtAuthFilter]의
 * 검증 대상 경로(/internal/gateway/)에 속하므로 유효한 Authorization Bearer 토큰이 반드시
 * 필요하다. 게이트웨이 내부 관측 데이터(엔드포인트 호출 패턴, 상태 코드 등)가 외부에 무단
 * 노출되지 않도록 인가된 요청자에게만 조회를 허용하기 위함이다.
 *
 * 민감정보 보호:
 * 요청/응답 본문, 쿼리스트링, 인증 헤더 등 PII나 보안상 민감한 자격증명은 저장 및 반환하지
 * 않고 HTTP 메서드, 경로, 상태 코드, 소요 시간 등 최소한의 메타데이터만 반환한다.
 */
@org.springframework.web.bind.annotation.ResponseBody
@RequestMapping("/internal/gateway/requests")
class RequestVisibilityController(
    private val eventStore: RequestEventStore,
) {
    @GetMapping
    fun recent(): List<RequestVisibilityEvent> =
        eventStore.recent()
}
