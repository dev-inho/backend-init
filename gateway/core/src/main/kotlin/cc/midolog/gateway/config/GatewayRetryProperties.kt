package cc.midolog.gateway.config

import jakarta.annotation.PostConstruct
import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties(prefix = "gateway.proxy.retry")
data class GatewayRetryProperties(
    var maxAttempts: Int = 1,
    var backoff: Duration = Duration.ofMillis(100)
) {
    @PostConstruct
    fun validate() {
        require(maxAttempts in 1..3) { "gateway.proxy.retry.max-attempts must be between 1 and 3. Found: $maxAttempts" }
        require(!backoff.isNegative) { "gateway.proxy.retry.backoff cannot be negative." }
    }
}
