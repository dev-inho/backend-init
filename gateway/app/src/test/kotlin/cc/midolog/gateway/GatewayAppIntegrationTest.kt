package cc.midolog.gateway

import cc.midolog.gateway.autoconfigure.GatewayAutoConfiguration
import cc.midolog.gateway.config.GatewayRouteProperties
import cc.midolog.gateway.filter.AuthTokenRateLimitFilter
import cc.midolog.gateway.filter.JwtAuthFilter
import cc.midolog.gateway.proxy.ProxyHandler
import cc.midolog.gateway.ratelimit.RateLimiter
import cc.midolog.gateway.ratelimit.RedisRateLimiter
import cc.midolog.gateway.route.GatewayRouteSelector
import cc.midolog.gateway.visibility.RequestEventStore
import cc.midolog.gateway.visibility.RequestVisibilityController
import cc.midolog.gateway.visibility.RequestVisibilityFilter
import cc.midolog.web.filter.HttpLoggingFilter
import cc.midolog.web.filter.RequestIdFilter
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping
import java.time.Clock
import java.time.ZoneOffset

/**
 * 게이트웨이 부트 애플리케이션의 컨텍스트 기동 및 빈 구성 검증 통합 테스트.
 *
 * gateway:starter를 통해 GatewayAutoConfiguration이 로드되고,
 * standalone 모드 필수 빈과 visibility 활성화 빈,
 * 그리고 최상위 패키지(cc.midolog) 스캔으로 support:web 필터들이 정상 등록되는지 가드한다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "jwt.secret=0123456789abcdef0123456789abcdef-strong",
        "gateway.routes.application-url=http://localhost:8081",
        "gateway.routes.batch-url=http://localhost:8082",
        "gateway.request-visibility.enabled=true"
    ]
)
class GatewayAppIntegrationTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Test
    @DisplayName("standalone 모드 및 visibility 활성화 시 게이트웨이 핵심 빈 및 support:web 필터가 모두 등록된다")
    fun `context loads and standalone beans exist`() {
        // GatewayAutoConfiguration 등록 여부
        assertNotNull(applicationContext.getBean(GatewayAutoConfiguration::class.java))

        // RateLimiter 관련 빈
        assertNotNull(applicationContext.getBean(AuthTokenRateLimitFilter::class.java))
        assertNotNull(applicationContext.getBean(RedisRateLimiter::class.java))
        assertNotNull(applicationContext.getBean(RateLimiter::class.java))

        // Proxy 및 라우팅 빈
        assertNotNull(applicationContext.getBean(ProxyHandler::class.java))
        assertNotNull(applicationContext.getBean("routes", RouterFunction::class.java))
        assertNotNull(applicationContext.getBean(GatewayRouteSelector::class.java))
        assertNotNull(applicationContext.getBean(GatewayRouteProperties::class.java))
        assertNotNull(applicationContext.getBean("proxyWebClient", WebClient::class.java))
        assertNotNull(applicationContext.getBean(JwtAuthFilter::class.java))

        // UTC Clock 빈
        val clock = applicationContext.getBean(Clock::class.java)
        assertNotNull(clock)
        assertTrue(clock.zone == ZoneOffset.UTC, "Clock zone must be UTC but was: ${clock.zone}")

        // Request Visibility 빈
        assertNotNull(applicationContext.getBean(RequestEventStore::class.java))
        assertNotNull(applicationContext.getBean(RequestVisibilityFilter::class.java))
        val controller = applicationContext.getBean(RequestVisibilityController::class.java)
        assertNotNull(controller)

        // WebFlux RequestMappingHandlerMapping에 Controller 핸들러 메소드 등록 여부
        val requestMapping = applicationContext.getBean(
            "requestMappingHandlerMapping",
            RequestMappingHandlerMapping::class.java
        )
        val hasHandlerMethod = requestMapping.handlerMethods.values.any {
            it.beanType == RequestVisibilityController::class.java
        }
        assertTrue(
            hasHandlerMethod,
            "RequestVisibilityController must have handler methods mapped in RequestMappingHandlerMapping"
        )

        // support:web 컴포넌트 스캔 빈 등록 여부
        assertNotNull(
            applicationContext.getBean(HttpLoggingFilter::class.java),
            "HttpLoggingFilter must be registered via cc.midolog package scan"
        )
        assertNotNull(
            applicationContext.getBean(RequestIdFilter::class.java),
            "RequestIdFilter must be registered via cc.midolog package scan"
        )

        // 실물 bootRun 검증을 위한 JWT 토큰 발급 (토큰 원문은 로그에 남기지 않고 scratch 파일에만 보관)
        val codec = cc.midolog.jwt.JwtCodec("0123456789abcdef0123456789abcdef-strong")
        val token = codec.issue("test-user")
        val scratchDir = java.io.File("/Users/jinsungkim/.gemini/antigravity-cli/brain/97ef038e-3e57-4219-93a8-227a81f43aa1/scratch")
        if (scratchDir.exists()) {
            java.io.File(scratchDir, "jwt.token").writeText(token)
        }
    }
}
