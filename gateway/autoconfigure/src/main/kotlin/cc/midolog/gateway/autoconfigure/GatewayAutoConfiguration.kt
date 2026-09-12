package cc.midolog.gateway.autoconfigure

import cc.midolog.gateway.config.GatewayClockConfig
import cc.midolog.gateway.config.GatewayRouteProperties
import cc.midolog.gateway.config.RouteConfig
import cc.midolog.gateway.config.WebClientConfig
import cc.midolog.gateway.filter.AuthTokenRateLimitFilter
import cc.midolog.gateway.filter.JwtAuthFilter
import cc.midolog.gateway.proxy.ProxyHandler
import cc.midolog.gateway.ratelimit.RateLimiter
import cc.midolog.gateway.ratelimit.RedisRateLimiter
import cc.midolog.gateway.route.GatewayRouteSelector
import cc.midolog.gateway.visibility.RequestEventStore
import cc.midolog.gateway.visibility.RequestVisibilityController
import cc.midolog.gateway.visibility.RequestVisibilityFilter
import cc.midolog.gateway.visibility.RequestVisibilityProperties
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.web.reactive.function.client.WebClient

/**
 * 게이트웨이 라이브러리의 자동 설정 엔트리포인트.
 *
 * 이 클래스는 `gateway.mode` 값을 검증하여 `embedded`, `standalone`, `remote` 중 하나가 아니면 컨텍스트 기동을 즉시 실패시킨다. 기존의 광범위한 패키지 스캔 방식 대신 역할이 나뉜 설정 클래스를 명시적으로 Import하고 빈을 등록하여 패키지 격리 원칙을 준수한다. 특히 `embedded` 모드에서는 애플리케이션의 컨트롤러가 요청을 직접 처리해야 한다. WebFlux의 Functional Router는 컨트롤러 매핑보다 우선순위가 높기 때문에, `embedded` 모드에서는 프록시 라우터와 인증 필터 빈을 등록하지 않아 요청을 가로채지 않도록 구성한다.
 */
@AutoConfiguration
@EnableConfigurationProperties(GatewayModeProperties::class, RequestVisibilityProperties::class)
@Import(GatewayClockConfig::class)
class GatewayAutoConfiguration {

    @Bean
    fun redisRateLimiter(
        template: ReactiveStringRedisTemplate,
        @org.springframework.beans.factory.annotation.Value("\${gateway.rate-limit.auth-token.limit:10}") limit: Long,
        @org.springframework.beans.factory.annotation.Value("\${gateway.rate-limit.auth-token.window-seconds:60}") windowSeconds: Long
    ): RedisRateLimiter = RedisRateLimiter(template, limit, windowSeconds)

    @Bean
    fun authTokenRateLimitFilter(rateLimiter: RateLimiter): AuthTokenRateLimitFilter =
        AuthTokenRateLimitFilter(rateLimiter)

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "gateway.request-visibility", name = ["enabled"], havingValue = "true")
    class VisibilityConfiguration {
        @Bean
        fun requestEventStore(properties: RequestVisibilityProperties): RequestEventStore =
            RequestEventStore(properties)

        @Bean
        fun requestVisibilityFilter(store: RequestEventStore): RequestVisibilityFilter =
            RequestVisibilityFilter(store)

        @Bean
        @ConditionalOnMissingBean(RequestVisibilityController::class)
        fun requestVisibilityController(store: RequestEventStore): RequestVisibilityController =
            RequestVisibilityController(store)
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnExpression("'\${gateway.mode:}' == 'standalone' || '\${gateway.mode:}' == 'remote'")
    @EnableConfigurationProperties(GatewayRouteProperties::class)
    @Import(WebClientConfig::class, RouteConfig::class)
    class ProxyConfiguration {
        @Bean
        fun gatewayRouteSelector(properties: GatewayRouteProperties): GatewayRouteSelector =
            GatewayRouteSelector(properties)

        @Bean
        fun proxyHandler(
            proxyWebClient: WebClient,
            routeSelector: GatewayRouteSelector,
        ): ProxyHandler = ProxyHandler(proxyWebClient, routeSelector)

        @Bean
        fun jwtAuthFilter(env: Environment): JwtAuthFilter {
            val secret = env.getProperty("jwt.secret")
            require(!secret.isNullOrBlank()) { "jwt.secret is required for proxy modes" }
            return JwtAuthFilter(secret)
        }
    }
}
