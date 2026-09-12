package cc.midolog.gateway.route

import cc.midolog.gateway.config.GatewayRouteProperties
import java.net.URI
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
 * 초기화 및 fail-fast 검증:
 * 생성자 초기화 시점에 설정된 모든 URL의 절대 경로 형식(http/https 스킴 및 호스트 존재)을 엄격히 검증한다.
 * 잘못된 URL 설정으로 인해 런타임에 라우팅 장애가 발생하는 대신 애플리케이션 기동 단계에서 즉시 실패(fail-fast)하도록 보장한다.
 */
class GatewayRouteSelector(
    properties: GatewayRouteProperties,
) {
    private val applicationTargets: List<String> = normalizeApplicationTargets(properties)
    private val batchTarget: String = validateUrl("gateway.routes.batch-url", properties.batchUrl)
    private val nextApplicationIndex = AtomicInteger(0)

    /**
     * 요청 경로에 따른 프록시 대상 베이스 URL을 반환한다.
     *
     * /batch/ 접두사 경로는 배치 타겟으로, 그 외 /api/ 및 /actuator/를 포함한 모든 요청은
     * 라운드로빈 카운터에 따라 애플리케이션 타겟 목록 중 하나로 전달한다.
     */
    fun selectTarget(path: String): String =
        when {
            path.startsWith("/batch/") -> batchTarget
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
