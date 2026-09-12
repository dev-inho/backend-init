package cc.midolog.gateway.route

import cc.midolog.gateway.config.GatewayRouteProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.DisposableBean
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.Disposable
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class GatewayRouteSelector(
    private val properties: GatewayRouteProperties,
    private val webClient: WebClient,
    private val clock: Clock,
    private val meterRegistry: MeterRegistry? = null
) : DisposableBean {

    private val log = LoggerFactory.getLogger(GatewayRouteSelector::class.java)
    private val applicationTargets: List<String> = normalizeApplicationTargets(properties)
    private val batchTarget: String = validateUrl("gateway.routes.batch-url", properties.batchUrl)
    private val nextApplicationIndex = AtomicInteger(0)

    class TargetState(
        @Volatile var isHealthy: Boolean = true,
        var successStreak: Int = 0,
        var failureStreak: Int = 0,
        @Volatile var lastCheck: Instant = Instant.MIN
    ) {
        val lock = Any()
    }

    private val targetStates = applicationTargets.associateWith { TargetState() }
    private var healthCheckDisposable: Disposable? = null

    init {
        targetStates.forEach { (target, state) ->
            meterRegistry?.gauge(
                "gateway.routes.healthy",
                listOf(Tag.of("target", target)),
                state
            ) { s -> if (s.isHealthy) 1.0 else 0.0 }
        }

        if (properties.healthCheck.enabled) {
            healthCheckDisposable = Flux.interval(properties.healthCheck.interval)
                .flatMap {
                    Flux.fromIterable(applicationTargets)
                        .flatMap { target -> checkHealth(target) }
                }
                .subscribe()
        }
    }

    override fun destroy() {
        healthCheckDisposable?.dispose()
    }

    fun selectTarget(path: String): String =
        when {
            path.startsWith("/batch/") -> batchTarget
            else -> nextApplicationTarget()
        }

    fun markUnhealthy(target: String) {
        val state = targetStates[target] ?: return
        val now = clock.instant()

        synchronized(state.lock) {
            state.lastCheck = now
            state.successStreak = 0
            state.failureStreak = properties.healthCheck.unhealthyThreshold
            if (state.isHealthy) {
                state.isHealthy = false
                log.warn("Target {} is now unhealthy manually at {}", target, now)
            }
        }
    }

    private fun checkHealth(target: String): Mono<Void> {
        val healthUrl = target.trimEnd('/') + properties.healthCheck.path
        return webClient.get()
            .uri(healthUrl)
            .exchangeToMono { response ->
                val isSuccess = response.statusCode().is2xxSuccessful
                response.releaseBody().then(Mono.fromRunnable<Void> { updateState(target, isSuccess) })
            }
            .onErrorResume { _ ->
                Mono.fromRunnable<Void> { updateState(target, false) }
            }
    }

    private fun updateState(target: String, isSuccess: Boolean) {
        val state = targetStates[target] ?: return
        val now = clock.instant()

        synchronized(state.lock) {
            state.lastCheck = now
            if (isSuccess) {
                state.failureStreak = 0
                state.successStreak++
                if (!state.isHealthy && state.successStreak >= properties.healthCheck.healthyThreshold) {
                    state.isHealthy = true
                    log.info("Target {} is now healthy at {}", target, now)
                }
            } else {
                state.successStreak = 0
                state.failureStreak++
                if (state.isHealthy && state.failureStreak >= properties.healthCheck.unhealthyThreshold) {
                    state.isHealthy = false
                    log.warn("Target {} is now unhealthy at {}", target, now)
                }
            }
        }
    }

    private fun nextApplicationTarget(): String {
        val healthyTargets = applicationTargets.filter { targetStates[it]?.isHealthy == true }
        val targetsToUse = healthyTargets.ifEmpty { applicationTargets }

        val index = nextApplicationIndex.getAndUpdate { current ->
            if (current == Int.MAX_VALUE) 0 else current + 1
        }
        return targetsToUse[Math.floorMod(index, targetsToUse.size)]
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
