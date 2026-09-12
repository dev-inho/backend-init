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

/**
 * 인입 요청 경로에 따라 프록시 대상 업스트림 URL을 결정하고 분산하는 라우트 선택기.
 *
 * 라우팅 분기 규칙:
 * - /batch/ 접두사 요청은 배치 전용 서버(gateway.routes.batch-url)로 라우팅한다.
 * - /actuator/ 및 /api/ 등을 포함한 그 외 모든 경로는 비즈니스 애플리케이션 서버로 라우팅한다.
 *   게이트웨이 자체의 액추에이터가 아니라 비즈니스 서버의 헬스체크 및 운영 지표를 중계하기 위함이다.
 *
 * 라운드로빈 분산:
 * 애플리케이션 대상 URL이 여러 개(gateway.routes.application-urls) 설정된 경우 [AtomicInteger] 기반
 * 카운터를 사용하여 라운드로빈 방식으로 대상을 선택한다. 카운터가 [Int.MAX_VALUE]에 도달하면 0으로
 * 원자적으로 회전시켜 오버플로로 인한 음수 인덱스 발생을 방지한다.
 *
 * 헬스체크 및 Fail-Open 정책:
 * 백그라운드에서 Flux.interval을 통해 주기적인 상태 점검을 수행하며, 지정된 threshold만큼 상태가 연속으로
 * 성공/실패할 때만 상태를 전환한다. 초기 상태는 항상 healthy로 간주한다.
 * 모든 대상 애플리케이션 타겟이 unhealthy로 판정될 경우, 트래픽을 차단하는 대신 설정된 전체 대상 목록을
 * 반환하여(fail-open) 라운드로빈을 계속 진행한다. 이는 일시적인 네트워크 파티션이나 헬스체크 로직 오류로 인한 전체 서비스 중단을 방지하는 fail-open 방향의 트레이드오프다.
 *
 * 초기화 및 fail-fast 검증:
 * 생성자 초기화 시점에 설정된 모든 URL의 절대 경로 형식(http/https 스킴 및 호스트 존재)을 엄격히 검증한다.
 * 잘못된 URL 설정으로 인해 런타임에 라우팅 장애가 발생하는 대신 애플리케이션 기동 단계에서 즉시 실패(fail-fast)하도록 보장한다.
 */
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

    /**
     * 컨텍스트 종료 시 백그라운드 헬스체크 subscription을 해제하여 리소스 누수를 방지한다.
     */
    override fun destroy() {
        healthCheckDisposable?.dispose()
    }

    fun selectTarget(path: String): String =
        when {
            path.startsWith("/batch/") -> batchTarget
            else -> nextApplicationTarget()
        }

    /**
     * 실제 클라이언트 요청 프록시 과정에서 ConnectException 등 네트워크 레벨의 연결 실패가 발생했을 때
     * 해당 타겟을 즉시 unhealthy 상태로 강제 전환한다.
     * threshold 정책을 우회하여 즉각적으로 라우팅 제외를 수행함으로써 다른 요청들이 타임아웃/연결 거부로 인해 지연되는 것을 방지하는 목적이다.
     */
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
