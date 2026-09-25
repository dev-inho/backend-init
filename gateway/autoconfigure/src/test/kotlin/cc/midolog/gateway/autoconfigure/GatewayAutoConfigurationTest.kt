package cc.midolog.gateway.autoconfigure

import cc.midolog.gateway.circuitbreaker.CircuitBreakerRegistry
import cc.midolog.gateway.config.GatewayCircuitBreakerProperties
import cc.midolog.gateway.config.GatewayRouteProperties
import cc.midolog.gateway.filter.AuthTokenRateLimitFilter
import cc.midolog.gateway.filter.JwtAuthFilter
import cc.midolog.gateway.proxy.ProxyHandler
import cc.midolog.gateway.ratelimit.RateLimiter
import cc.midolog.gateway.ratelimit.RedisRateLimiter
import cc.midolog.gateway.route.GatewayRouteSelector
import cc.midolog.gateway.visibility.RequestEventStore
import cc.midolog.gateway.visibility.RequestVisibilityHandler
import cc.midolog.gateway.visibility.RequestVisibilityFilter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner
import org.springframework.boot.webflux.autoconfigure.WebFluxAutoConfiguration
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.result.method.annotation.RequestMappingHandlerMapping
import org.springframework.web.reactive.function.server.support.RouterFunctionMapping
import java.time.Clock

class GatewayAutoConfigurationTest {

    private val contextRunner = ReactiveWebApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            GatewayAutoConfiguration::class.java,
            WebFluxAutoConfiguration::class.java
        ))
        .withUserConfiguration(TestConfig::class.java)

    @org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
    class TestConfig {
        @org.springframework.context.annotation.Bean
        fun reactiveStringRedisTemplate(): org.springframework.data.redis.core.ReactiveStringRedisTemplate =
            org.mockito.Mockito.mock(org.springframework.data.redis.core.ReactiveStringRedisTemplate::class.java)
    }

    private fun contextWithProps(vararg props: String) = contextRunner.withPropertyValues(*props)

    @Test
    fun `gateway mode 미설정시 fail-fast`() {
        contextRunner.run { context ->
            assertTrue(context.startupFailure != null)
            val rootCauseMessage = generateSequence(context.startupFailure) { it.cause }.joinToString { it.message ?: "" }
            assertTrue(rootCauseMessage.contains("gateway.mode must be exactly one of"), "Message was: $rootCauseMessage")
        }
    }

    @Test
    fun `gateway mode 잘못된 값 입력시 fail-fast`() {
        contextRunner.withPropertyValues("gateway.mode=invalid").run { context ->
            assertTrue(context.startupFailure != null)
            val rootCauseMessage = generateSequence(context.startupFailure) { it.cause }.joinToString { it.message ?: "" }
            assertTrue(rootCauseMessage.contains("gateway.mode must be exactly one of"), "Message was: $rootCauseMessage")
        }
    }

    @Test
    fun `standalone 모드 빈 구성 가드`() {
        contextWithProps(
            "gateway.mode=standalone",
            "jwt.secret=this_is_a_test_secret_for_jwt_auth_filter",
            "gateway.routes.application-url=http://localhost:8081",
            "gateway.routes.batch-url=http://localhost:8082",
            "gateway.request-visibility.enabled=true"
        ).run { context ->
            assertNull(context.startupFailure)

            // 공통 빈
            assertTrue(context.containsBean("authTokenRateLimitFilter"))
            assertNotNull(context.getBean(AuthTokenRateLimitFilter::class.java))
            assertNotNull(context.getBean(RedisRateLimiter::class.java))
            assertNotNull(context.getBean(RateLimiter::class.java))
            assertNotNull(context.getBean(Clock::class.java))

            // Visibility 빈
            assertNotNull(context.getBean(RequestVisibilityFilter::class.java))
            assertNotNull(context.getBean(RequestEventStore::class.java))
            assertNotNull(context.getBean(RequestVisibilityHandler::class.java))

            // Proxy 빈
            assertNotNull(context.getBean(ProxyHandler::class.java))
            assertNotNull(context.getBean("routes", RouterFunction::class.java))
            assertNotNull(context.getBean(WebClient::class.java))
            assertNotNull(context.getBean(GatewayRouteSelector::class.java))
            assertNotNull(context.getBean(GatewayRouteProperties::class.java))
            assertNotNull(context.getBean(GatewayCircuitBreakerProperties::class.java))
            assertNotNull(context.getBean(CircuitBreakerRegistry::class.java))
            assertNotNull(context.getBean(JwtAuthFilter::class.java))

            val visibilityRoutes = context.getBean("visibilityRoutes", RouterFunction::class.java)
            assertNotNull(visibilityRoutes, "visibilityRoutes must be registered as a RouterFunction")
        }
    }

    @Test
    fun `remote 모드 빈 구성 가드`() {
        contextWithProps(
            "gateway.mode=remote",
            "jwt.secret=this_is_a_test_secret_for_jwt_auth_filter",
            "gateway.routes.application-url=http://localhost:8081",
            "gateway.routes.batch-url=http://localhost:8082",
            "gateway.request-visibility.enabled=true"
        ).run { context ->
            assertNull(context.startupFailure)

            // 공통 빈
            assertTrue(context.containsBean("authTokenRateLimitFilter"))
            assertNotNull(context.getBean(AuthTokenRateLimitFilter::class.java))
            assertNotNull(context.getBean(RedisRateLimiter::class.java))
            assertNotNull(context.getBean(RateLimiter::class.java))
            assertNotNull(context.getBean(Clock::class.java))

            // Visibility 빈
            assertNotNull(context.getBean(RequestVisibilityFilter::class.java))
            assertNotNull(context.getBean(RequestEventStore::class.java))
            assertNotNull(context.getBean(RequestVisibilityHandler::class.java))

            // Proxy 빈
            assertNotNull(context.getBean(ProxyHandler::class.java))
            assertNotNull(context.getBean("routes", RouterFunction::class.java))
            assertNotNull(context.getBean(WebClient::class.java))
            assertNotNull(context.getBean(GatewayRouteSelector::class.java))
            assertNotNull(context.getBean(GatewayRouteProperties::class.java))
            assertNotNull(context.getBean(GatewayCircuitBreakerProperties::class.java))
            assertNotNull(context.getBean(CircuitBreakerRegistry::class.java))
            assertNotNull(context.getBean(JwtAuthFilter::class.java))
        }
    }

    @Test
    fun `embedded 모드 빈 구성 가드`() {
        contextWithProps(
            "gateway.mode=embedded",
            "gateway.request-visibility.enabled=true"
        ).run { context ->
            assertNull(context.startupFailure)

            // 공통 빈
            assertNotNull(context.getBean(AuthTokenRateLimitFilter::class.java))
            assertNotNull(context.getBean(RedisRateLimiter::class.java))
            assertNotNull(context.getBean(RateLimiter::class.java))
            assertNotNull(context.getBean(Clock::class.java))

            // Visibility 빈
            assertNotNull(context.getBean(RequestVisibilityFilter::class.java))
            assertNotNull(context.getBean(RequestEventStore::class.java))
            assertNotNull(context.getBean(RequestVisibilityHandler::class.java))

            // Proxy 및 Breaker 빈 존재 안함 (이름 + 타입 단언)
            assertFalse(context.containsBean("proxyHandler"))
            assertFalse(context.containsBean("routes"))
            assertFalse(context.containsBean("gatewayRouteSelector"))
            assertFalse(context.containsBean("circuitBreakerRegistry"))
            assertFalse(context.containsBean("jwtAuthFilter"))
            assertTrue(context.getBeansOfType(GatewayRouteProperties::class.java).isEmpty())
            assertTrue(context.getBeansOfType(GatewayCircuitBreakerProperties::class.java).isEmpty())
            assertTrue(context.getBeansOfType(CircuitBreakerRegistry::class.java).isEmpty())
            assertTrue(context.getBeansOfType(WebClient::class.java).isEmpty())

            // Visibility RouterFunction 확인 및 /api/** 미매칭 검증
            val visibilityRoutes = context.getBean("visibilityRoutes", RouterFunction::class.java)
            assertNotNull(visibilityRoutes)

            // 실제 route predicate/요청으로 검증
            val apiExchange = org.springframework.mock.web.server.MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/api/some-endpoint").build()
            )
            val apiRequest = org.springframework.mock.web.reactive.function.server.MockServerRequest.builder()
                .method(org.springframework.http.HttpMethod.GET)
                .uri(java.net.URI("/api/some-endpoint"))
                .exchange(apiExchange)
                .build()

            val visibilityExchange = org.springframework.mock.web.server.MockServerWebExchange.from(
                org.springframework.mock.http.server.reactive.MockServerHttpRequest.get("/internal/gateway/requests").build()
            )
            val visibilityRequest = org.springframework.mock.web.reactive.function.server.MockServerRequest.builder()
                .method(org.springframework.http.HttpMethod.GET)
                .uri(java.net.URI("/internal/gateway/requests"))
                .exchange(visibilityExchange)
                .build()

            assertTrue(visibilityRoutes.route(apiRequest).blockOptional().isEmpty, "/api/** should not be matched by visibility routes")
            assertTrue(visibilityRoutes.route(visibilityRequest).blockOptional().isPresent, "/internal/gateway/requests should be matched")
        }
    }

    @Test
    fun `WebFlux 매핑 순서 실측 가드`() {
        // embedded 모드에서 routes 빈이 등록되면 /api/** 를 가로채는 원인을 검증
        contextWithProps(
            "gateway.mode=standalone",
            "jwt.secret=this_is_a_test_secret_for_jwt_auth_filter",
            "gateway.routes.application-url=http://localhost:8081",
            "gateway.routes.batch-url=http://localhost:8082"
        ).run { context ->
            val routerMapping = context.getBean("routerFunctionMapping", RouterFunctionMapping::class.java)
            val requestMapping = context.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping::class.java)

            // Functional 라우터가 Controller 매핑보다 우선순위가 높은지 실측
            assertEquals(-1, routerMapping.order)
            assertEquals(0, requestMapping.order)
        }
    }

    @Test
    fun `AutoConfiguration imports 리소스 가드`() {
        val stream = this::class.java.classLoader.getResourceAsStream(
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports"
        )
        assertNotNull(stream, "imports file should exist")
        val lines = stream!!.bufferedReader().readLines().filter { it.isNotBlank() }
        assertTrue(lines.size == 1 && lines[0] == "cc.midolog.gateway.autoconfigure.GatewayAutoConfiguration", "imports file must contain exactly the FQCN of GatewayAutoConfiguration")
    }

    @Test
    fun `visibility beans are disabled by default`() {
        contextWithProps("gateway.mode=embedded").run { context ->
            assertNull(context.startupFailure)
            assertTrue(context.getBeansOfType(RequestVisibilityFilter::class.java).isEmpty())
            assertTrue(context.getBeansOfType(RequestEventStore::class.java).isEmpty())
            assertTrue(context.getBeansOfType(RequestVisibilityHandler::class.java).isEmpty())
        }
    }

    @Test
    fun `visibility beans are enabled only when property is true`() {
        contextWithProps(
            "gateway.mode=embedded",
            "gateway.request-visibility.enabled=true"
        ).run { context ->
            assertNull(context.startupFailure)
            assertFalse(context.getBeansOfType(RequestVisibilityFilter::class.java).isEmpty())
            assertFalse(context.getBeansOfType(RequestEventStore::class.java).isEmpty())
            assertFalse(context.getBeansOfType(RequestVisibilityHandler::class.java).isEmpty())
        }
    }
}
