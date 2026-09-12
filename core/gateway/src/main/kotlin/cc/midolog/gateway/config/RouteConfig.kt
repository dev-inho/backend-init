package cc.midolog.gateway.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router

/**
 * 게이트웨이 라우팅 설정.
 * 구체적인 핸들러 구현체에 의존하지 않고 HandlerFunction 인터페이스를 주입받아 라우트를 구성한다.
 */
@Configuration
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
