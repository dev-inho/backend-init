package cc.midolog.gateway.visibility

import java.time.Instant
import java.util.ArrayDeque


/**
 * 최근 처리된 요청 가시성 이벤트를 메모리에 보관하는 링 버퍼 저장소.
 *
 * GatewayAutoConfiguration에 의해 `gateway.request-visibility.enabled=true` 프로퍼티가 활성화된 경우에만 빈으로 등록된다.
 * [ArrayDeque]를 기반으로 동작하며, 설정된 [RequestVisibilityProperties.capacity](최소 1) 크기만큼
 * 최근 이벤트를 유지한다. 새 이벤트가 추가될 때 용량을 초과하면 가장 오래된 이벤트를 버린다.
 *
 * 동시성 제어를 위해 [record]와 [recent] 메서드에 동기화([Synchronized])를 적용한다.
 */
class RequestEventStore(
    properties: RequestVisibilityProperties,
) {
    private val capacity = properties.capacity.coerceAtLeast(1)
    private val events = ArrayDeque<RequestVisibilityEvent>(capacity)

    /**
     * 링 버퍼의 선두에 새 가시성 이벤트를 추가하고 용량을 초과한 오래된 이벤트를 제거한다.
     */
    @Synchronized
    fun record(event: RequestVisibilityEvent) {
        events.addFirst(event)
        while (events.size > capacity) {
            events.removeLast()
        }
    }

    @Synchronized
    fun recent(): List<RequestVisibilityEvent> =
        events.toList()
}
