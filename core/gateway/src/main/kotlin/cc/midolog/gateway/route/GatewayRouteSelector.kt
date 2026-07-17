package cc.midolog.gateway.route

import cc.midolog.gateway.config.GatewayRouteProperties
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import org.springframework.stereotype.Component

@Component
class GatewayRouteSelector(
    properties: GatewayRouteProperties,
) {
    private val applicationTargets: List<String> = normalizeApplicationTargets(properties)
    private val batchTarget: String = validateUrl("gateway.routes.batch-url", properties.batchUrl)
    private val nextApplicationIndex = AtomicInteger(0)

    fun selectTarget(path: String): String =
        when {
            path.startsWith("/batch/") -> batchTarget
            path.startsWith("/api/") || path.startsWith("/actuator/") -> nextApplicationTarget()
            else -> nextApplicationTarget()
        }

    private fun nextApplicationTarget(): String {
        val index = nextApplicationIndex.getAndUpdate { current ->
            if (current == Int.MAX_VALUE) 0 else current + 1
        }
        return applicationTargets[Math.floorMod(index, applicationTargets.size)]
    }

    private fun normalizeApplicationTargets(properties: GatewayRouteProperties): List<String> {
        val configuredTargets = properties.applicationUrls
            .map(String::trim)
            .filter(String::isNotEmpty)
        val targets = configuredTargets.ifEmpty { listOf(properties.applicationUrl.trim()) }
        check(targets.isNotEmpty() && targets.none(String::isBlank)) {
            "gateway.routes.application-url or application-urls must define at least one target"
        }
        return targets.mapIndexed { index, target ->
            val propertyName = if (configuredTargets.isEmpty()) {
                "gateway.routes.application-url"
            } else {
                "gateway.routes.application-urls[$index]"
            }
            validateUrl(propertyName, target)
        }
    }

    private fun validateUrl(propertyName: String, value: String): String {
        val uri = runCatching { URI.create(value.trim()) }.getOrElse {
            throw IllegalStateException("$propertyName must be an absolute http(s) URL: $value")
        }
        val scheme = uri.scheme
        if (scheme != "http" && scheme != "https" || uri.host.isNullOrBlank()) {
            throw IllegalStateException("$propertyName must be an absolute http(s) URL: $value")
        }
        return value.trim().trimEnd('/')
    }
}
