package cc.midolog.gateway.visibility

import cc.midolog.logging.LoggingMdc
import java.time.Clock
import java.time.Instant
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@Component
@Order(100)
@ConditionalOnProperty(prefix = "gateway.request-visibility", name = ["enabled"], havingValue = "true")
class RequestVisibilityFilter(
    private val eventStore: RequestEventStore,
    private val clock: Clock = Clock.systemUTC(),
) : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val startedAtMillis = clock.millis()
        return chain.filter(exchange)
            .doFinally {
                eventStore.record(
                    RequestVisibilityEvent(
                        method = exchange.request.method.name(),
                        path = exchange.request.path.pathWithinApplication().value(),
                        status = exchange.response.statusCode?.value(),
                        requestId = exchange.request.headers.getFirst(LoggingMdc.REQUEST_ID),
                        timestamp = Instant.ofEpochMilli(startedAtMillis),
                        durationMs = (clock.millis() - startedAtMillis).coerceAtLeast(0),
                    ),
                )
            }
    }
}
