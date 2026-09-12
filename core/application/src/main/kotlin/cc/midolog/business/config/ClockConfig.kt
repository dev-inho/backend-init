package cc.midolog.business.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 애플리케이션 내 시간 기반 로직이 결정적 결과를 낼 수 있도록 UTC 시각을 제공하는 기본 설정.
 * 단위 테스트나 통합 테스트에서 고정 시계(Fixed Clock)를 컨텍스트에 등록하면 
 * 이 기본 빈이 물러나 시간을 제어할 수 있게 된다.
 */
@Configuration
class ClockConfig {

    @Bean
    @ConditionalOnMissingBean(Clock::class)
    fun clock(): Clock {
        return Clock.systemUTC()
    }
}
