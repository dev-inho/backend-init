package cc.midolog.gateway.config

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "gateway.proxy.circuit-breaker")
data class GatewayCircuitBreakerProperties(
    var enabled: Boolean = true,
    var failureThreshold: Int = 3,
    var openDuration: Duration = Duration.ofSeconds(5),
    var halfOpenPermits: Int = 1,
    var maxConcurrentCalls: Int = 50
) {
    @PostConstruct
    fun validate() {
        require(failureThreshold >= 1) {
            "gateway.proxy.circuit-breaker.failure-threshold must be at least 1. Found: $failureThreshold"
        }
        require(!openDuration.isNegative && !openDuration.isZero) {
            "gateway.proxy.circuit-breaker.open-duration must be positive. Found: $openDuration"
        }
        require(halfOpenPermits >= 1) {
            "gateway.proxy.circuit-breaker.half-open-permits must be at least 1. Found: $halfOpenPermits"
        }
        require(maxConcurrentCalls >= 1) {
            "gateway.proxy.circuit-breaker.max-concurrent-calls must be at least 1. Found: $maxConcurrentCalls"
        }
    }
}
