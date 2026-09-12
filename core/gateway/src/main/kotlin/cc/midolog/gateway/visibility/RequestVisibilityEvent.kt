package cc.midolog.gateway.visibility

import java.time.Instant

/**
 * 프록시 요청의 가시성 정보를 담는 이벤트 데이터.
 * 게이트웨이를 통과한 요청의 경로, 상태, 소요 시간 등을 기록하기 위해 사용된다.
 */
data class RequestVisibilityEvent(
    val method: String,
    val path: String,
    val status: Int?,
    val requestId: String?,
    val timestamp: Instant,
    val durationMs: Long,
)
