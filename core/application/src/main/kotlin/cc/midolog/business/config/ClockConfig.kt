package cc.midolog.business.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/**
 * 애플리케이션 전반에서 사용할 시계(Clock) 빈을 등록하는 설정 클래스.
 * 테스트 시 고정된 시각을 주입할 수 있도록 인터페이스로 제공한다.
 */
@Configuration
class ClockConfig {

    @Bean
    fun clock(): Clock {
        return Clock.systemUTC()
    }
}
