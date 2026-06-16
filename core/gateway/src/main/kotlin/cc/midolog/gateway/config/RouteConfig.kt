package cc.midolog.gateway.config

import cc.midolog.gateway.handler.ProxyHandler
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

/**
 * 게이트웨이 라우팅 설정.
 * 경로 prefix를 ProxyHandler로 위임하여 비즈니스 서버로 분기한다.
 */
@Configuration
class RouteConfig(
    private val proxyHandler: ProxyHandler,
) {
    @Bean
    fun routes(): RouterFunction<ServerResponse> = router {
        path("/api/**").invoke(proxyHandler::proxy)
        path("/batch/**").invoke(proxyHandler::proxy)
        path("/actuator/**").invoke(proxyHandler::proxy)
    }
}
