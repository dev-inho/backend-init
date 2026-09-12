package cc.midolog.gateway.visibility

import java.time.Instant

/**
 * 게이트웨이를 통과한 개별 요청의 관측 메타데이터를 담는 불변 데이터 모델.
 *
 * [RequestVisibilityFilter]에 의해 생성되어 [RequestEventStore]에 버퍼링된다.
 * HTTP 메서드, 경로, 응답 상태 코드, X-Request-Id, 요청 시작 시각([timestamp]), 소요 시간([durationMs])을
 * 보관하며, PII나 민감한 자격증명 노출을 방지하기 위해 요청/응답 본문과 쿼리스트링, 인증 헤더는 보관하지 않는다.
 */
data class RequestVisibilityEvent(
    val method: String,
    val path: String,
    val status: Int?,
    val requestId: String?,
    val timestamp: Instant,
    val durationMs: Long,
)
