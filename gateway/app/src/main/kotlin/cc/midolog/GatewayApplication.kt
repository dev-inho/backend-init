package cc.midolog

import cc.midolog.gateway.visibility.RequestVisibilityController
import cc.midolog.logging.ReactorMdc
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.FilterType

/**
 * 게이트웨이 서비스를 구동하는 Spring Boot 진입점.
 * 최상위 패키지인 cc.midolog에 위치하여 컴포넌트 스캔을 통해 게이트웨이 내부 컴포넌트뿐만
 * 아니라 support:web 모듈의 웹 필터(HttpLoggingFilter, RequestIdFilter)를 자동으로 감지해 등록한다.
 *
 * W1 autoconfigure 전환 과정에서 core에 남겨진 RequestVisibilityController의 @RestController로 인한
 * 비활성화(enabled=false) 시점 빈 주입 오류를 방지하기 위해 해당 컨트롤러는 컴포넌트 스캔에서 제외하고,
 * GatewayAutoConfiguration의 조건부 등록(@Bean)에 전적으로 위임한다.
 */
@SpringBootApplication
@ComponentScan(
    basePackages = ["cc.midolog"],
    excludeFilters = [
        ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = [RequestVisibilityController::class]
        )
    ]
)
class GatewayApplication

/**
 * 게이트웨이 애플리케이션을 구동하고 리액티브 로깅 훅을 등록한다.
 * Spring 컨텍스트 기동 전에 ReactorMdc.enable()을 호출해 Reactor 연산자 체인 전반에
 * MDC 복사 훅을 등록한다. 이를 통해 리액티브 스레드 전환 시에도 X-Request-Id가 MDC로 전파된다.
 */
fun main(args: Array<String>) {
    ReactorMdc.enable()
    runApplication<GatewayApplication>(*args)
}
