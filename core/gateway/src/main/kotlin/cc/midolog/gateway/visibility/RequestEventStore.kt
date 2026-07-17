package cc.midolog.gateway.visibility

import java.time.Instant
import java.util.ArrayDeque
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

data class RequestVisibilityEvent(
    val method: String,
    val path: String,
    val status: Int?,
    val requestId: String?,
    val timestamp: Instant,
    val durationMs: Long,
)

@Component
@ConditionalOnProperty(prefix = "gateway.request-visibility", name = ["enabled"], havingValue = "true")
class RequestEventStore(
    properties: RequestVisibilityProperties,
) {
    private val capacity = properties.capacity.coerceAtLeast(1)
    private val events = ArrayDeque<RequestVisibilityEvent>(capacity)

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
