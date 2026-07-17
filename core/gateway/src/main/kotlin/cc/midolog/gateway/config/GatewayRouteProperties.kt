package cc.midolog.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "gateway.routes")
data class GatewayRouteProperties(
    var applicationUrl: String = "",
    var applicationUrls: List<String> = emptyList(),
    var batchUrl: String = "",
)
