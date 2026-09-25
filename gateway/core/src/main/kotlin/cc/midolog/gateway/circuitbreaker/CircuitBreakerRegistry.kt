package cc.midolog.gateway.circuitbreaker

import cc.midolog.gateway.config.GatewayCircuitBreakerProperties
import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

class CircuitBreakerRegistry(
    val properties: GatewayCircuitBreakerProperties = GatewayCircuitBreakerProperties(),
    val clock: Clock = Clock.systemUTC()
) {
    private val circuitBreakers = ConcurrentHashMap<String, CircuitBreaker>()

    fun getOrCreate(targetUrl: String): CircuitBreaker =
        circuitBreakers.computeIfAbsent(targetUrl) {
            CircuitBreaker(it, properties, clock)
        }

    fun get(targetUrl: String): CircuitBreaker? = circuitBreakers[targetUrl]

    fun all(): Map<String, CircuitBreaker> = circuitBreakers.toMap()
}
