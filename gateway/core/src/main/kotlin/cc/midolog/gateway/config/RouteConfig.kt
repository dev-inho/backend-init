package cc.midolog.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

/**
 * 인입 요청 경로를 프록시 핸들러로 매핑하는 WebFlux 라우팅 설정.
 *
 * 구체 핸들러 구현체(cc.midolog.gateway.proxy.ProxyHandler)에 직접 의존하지 않고
 * 스프링 표준 [HandlerFunction] 인터페이스를 주입받는다. 이는 `config` 패키지가
 * `proxy`나 `route` 패키지를 참조하지 못하도록 격리해 패키지 순환 의존성을 방지하기
 * 위함이며, 이 아키텍처 규칙은 `GatewayPackageDependencyTest`가 보증한다.
 *
 * 제약: 스프링 컨텍스트 내에 [HandlerFunction] 타입의 빈이 정확히 하나만 존재해야
 * 모호성 없이 주입된다. /api/, /batch/, /actuator/ 하위 경로를 프록시로 중계하며,
 * 게이트웨이 자체 내부 엔드포인트(/internal/gateway/)는 전용 컨트롤러가 처리하므로
 * 이 라우터에 포함하지 않는다.
 */
class RouteConfig(
    private val proxyHandlerFunction: HandlerFunction<ServerResponse>,
) {
    @Bean
    fun routes(): RouterFunction<ServerResponse> = router {
        path("/api/**").invoke(proxyHandlerFunction::handle)
        path("/batch/**").invoke(proxyHandlerFunction::handle)
        path("/actuator/**").invoke(proxyHandlerFunction::handle)
    }
}
