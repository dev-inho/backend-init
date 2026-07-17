package cc.midolog.gateway.visibility

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "gateway.request-visibility")
data class RequestVisibilityProperties(
    var enabled: Boolean = false,
    var capacity: Int = 200,
)
