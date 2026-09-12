package cc.midolog.gateway.config

import java.time.Clock
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * 게이트웨이 전역에서 사용할 표준 [Clock] 빈 설정.
 *
 * `@ConditionalOnMissingBean(Clock::class)`을 선언하여 기본적으로 UTC 시스템 시계
 * ([Clock.systemUTC])를 등록하되, 테스트 환경 등에서 고정 시계([Clock.fixed]) 같은
 * 대체 빈이 이미 등록되어 있다면 이를 덮어쓰지 않고 양보하도록 설계했다.
 */
@Configuration
class GatewayClockConfig {
    @Bean
    @ConditionalOnMissingBean(Clock::class)
    fun gatewayClock(): Clock = Clock.systemUTC()
}
