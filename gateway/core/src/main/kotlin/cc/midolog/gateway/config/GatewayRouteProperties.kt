package cc.midolog.gateway.config

import org.springframework.beans.factory.InitializingBean
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.NestedConfigurationProperty
import java.time.Duration

/**
 * 프록시 라우팅 대상 백엔드 서버 URL 설정 프로퍼티.
 *
 * `gateway.routes` 접두사로 바인딩되며, 각 프로퍼티와 환경변수 매핑은 다음과 같다:
 * - applicationUrl: 단일 비즈니스 애플리케이션 URL (환경변수 `GATEWAY_APPLICATION_URL`).
 * - applicationUrls: 여러 비즈니스 애플리케이션 인스턴스 URL 목록 (환경변수 `GATEWAY_APPLICATION_URLS`).
 *   비어 있지 않으면 라운드로빈 분산 대상 목록으로 우선 사용된다.
 * - batchUrl: 배치 전용 서버 URL (환경변수 `GATEWAY_BATCH_URL`).
 * - healthCheck: 대상 서버들의 가용성을 능동적으로 모니터링하기 위한 상태 점검 설정(옵션).
 */
@ConfigurationProperties(prefix = "gateway.routes")
data class GatewayRouteProperties(
    var applicationUrl: String = "",
    var applicationUrls: List<String> = emptyList(),
    var batchUrl: String = "",
    @NestedConfigurationProperty
    var healthCheck: GatewayHealthCheckProperties = GatewayHealthCheckProperties()
) : InitializingBean {
    override fun afterPropertiesSet() {
        require(healthCheck.interval.toMillis() > 0) { "gateway.routes.health-check.interval must be greater than 0" }
        require(healthCheck.unhealthyThreshold > 0) { "gateway.routes.health-check.unhealthy-threshold must be greater than 0" }
        require(healthCheck.healthyThreshold > 0) { "gateway.routes.health-check.healthy-threshold must be greater than 0" }
        require(healthCheck.path.startsWith("/")) { "gateway.routes.health-check.path must start with '/'" }
    }
}

/**
 * 백엔드 애플리케이션 서버를 주기적으로 핑(ping)하여 실패한 타겟을 라우팅 대상에서 제외하는 설정.
 *
 * interval 주기마다 path에 HTTP GET 요청을 보내 2xx 응답이 unhealthyThreshold만큼 연속 실패하면 해당 대상을 제외하며,
 * 제외된 타겟이 다시 healthyThreshold만큼 연속 성공하면 라우팅에 복귀시킨다.
 * 단, 네트워크 연결 실패 등의 즉시 복구 불가능한 에러 시에는 ProxyHandler를 통해 threshold와 무관하게 즉시 unhealthy로 마킹될 수 있다.
 */
data class GatewayHealthCheckProperties(
    var enabled: Boolean = false,
    var path: String = "/actuator/health",
    var interval: Duration = Duration.ofSeconds(10),
    var unhealthyThreshold: Int = 3,
    var healthyThreshold: Int = 1
)
