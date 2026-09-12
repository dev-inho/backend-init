package cc.midolog.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.time.Duration

@ConfigurationProperties(prefix = "gateway.routes")
data class GatewayRouteProperties(
    var applicationUrl: String = "",
    var applicationUrls: List<String> = emptyList(),
    var batchUrl: String = "",
    @NestedConfigurationProperty
    var healthCheck: GatewayHealthCheckProperties = GatewayHealthCheckProperties()
)

data class GatewayHealthCheckProperties(
    var enabled: Boolean = false,
    var path: String = "/actuator/health",
    var interval: Duration = Duration.ofSeconds(10),
    var unhealthyThreshold: Int = 3,
    var healthyThreshold: Int = 1
) {
    init {
        require(interval.toMillis() > 0) { "gateway.routes.health-check.interval must be greater than 0" }
        require(unhealthyThreshold > 0) { "gateway.routes.health-check.unhealthy-threshold must be greater than 0" }
        require(healthyThreshold > 0) { "gateway.routes.health-check.healthy-threshold must be greater than 0" }
        require(path.startsWith("/")) { "gateway.routes.health-check.path must start with '/'" }
    }
}
