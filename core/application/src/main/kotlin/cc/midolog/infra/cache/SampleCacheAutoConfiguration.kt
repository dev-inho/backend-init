package cc.midolog.infra.cache

import cc.midolog.sample.port.cache.SampleCachePort
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.core.Ordered
import org.springframework.data.redis.core.ReactiveStringRedisTemplate

/**
 * 기본 Redis 샘플 캐시 어댑터를 제공하는 자동 구성 클래스.
 *
 * 고객 모듈이나 애플리케이션 구성에서 [SampleCachePort] 빈을 직접 등록하지 않은 경우에만
 * [RedisSampleCacheAdapter]를 기본 캐시 어댑터 빈으로 등록한다.
 * 고객 모듈의 커스텀 자동 구성이 먼저 평가될 수 있도록 최하위 자동 구성 순서([Ordered.LOWEST_PRECEDENCE])를 가진다.
 */
@AutoConfiguration
@AutoConfigureOrder(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnClass(ReactiveStringRedisTemplate::class)
class SampleCacheAutoConfiguration {

    /**
     * [SampleCachePort] 구현체 빈이 컨텍스트에 없을 때 기본 Redis 어댑터를 등록한다.
     */
    @Bean
    @ConditionalOnMissingBean(SampleCachePort::class)
    fun redisSampleCacheAdapter(
        redis: ReactiveStringRedisTemplate,
    ): RedisSampleCacheAdapter = RedisSampleCacheAdapter(redis)
}
