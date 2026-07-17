package cc.midolog.gateway.visibility

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/gateway/requests")
@ConditionalOnProperty(prefix = "gateway.request-visibility", name = ["enabled"], havingValue = "true")
class RequestVisibilityController(
    private val eventStore: RequestEventStore,
) {
    @GetMapping
    fun recent(): List<RequestVisibilityEvent> =
        eventStore.recent()
}
