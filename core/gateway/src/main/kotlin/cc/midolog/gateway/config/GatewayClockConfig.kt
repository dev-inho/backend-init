package cc.midolog.gateway.config

import java.time.Clock
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class GatewayClockConfig {
    @Bean
    @ConditionalOnMissingBean(Clock::class)
    fun gatewayClock(): Clock = Clock.systemUTC()
}
