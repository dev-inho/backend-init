package cc.midolog.gateway.visibility

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

/**
 * 게이트웨이 요청 가시성(최근 요청 버퍼링 및 조회) 기능 설정 프로퍼티.
 *
 * `gateway.request-visibility` 접두사로 바인딩되며, 다음 속성을 포함한다:
 * - [enabled]: 요청 가시성 기능 활성화 여부(기본값 false). true 설정 시 [RequestEventStore],
 *   [RequestVisibilityFilter], [RequestVisibilityController]가 스프링 빈으로 등록된다.
 * - [capacity]: 링 버퍼에 보관할 최대 최근 요청 수(기본값 200, 최소 1).
 */
@ConfigurationProperties(prefix = "gateway.request-visibility")
data class RequestVisibilityProperties(
    var enabled: Boolean = false,
    var capacity: Int = 200,
)
