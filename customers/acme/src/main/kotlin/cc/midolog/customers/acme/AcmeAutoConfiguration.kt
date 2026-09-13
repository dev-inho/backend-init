package cc.midolog.customers.acme

import cc.midolog.customer.CustomerDescriptor
import cc.midolog.sample.model.Sample
import cc.midolog.sample.policy.SampleSavePolicy
import cc.midolog.sample.port.cache.SampleCachePort
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.web.reactive.function.server.RouterFunction
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter

/**
 * Acme 고객 전용 자동 구성 클래스.
 *
 * `app.customer=acme` 프로퍼티가 활성화된 경우에만 로드되며, 컴포넌트 스캔에 의존하지 않도록
 * 스테레오타입 어노테이션 대신 [@AutoConfiguration] 및 [@Bean] 메서드로만 빈을 노출한다.
 * Acme 고객용 식별자, 샘플 저장 정책, no-op 캐시 포트, 전용 라우터 엔드포인트를 제공한다.
 */
@AutoConfiguration
@ConditionalOnProperty(name = ["app.customer"], havingValue = "acme")
class AcmeAutoConfiguration {

    @Bean
    fun acmeCustomerDescriptor(): CustomerDescriptor =
        CustomerDescriptor(name = "acme")

    @Bean
    fun acmeSampleSavePolicy(): SampleSavePolicy =
        object : SampleSavePolicy {
            override val order: Int get() = 10
            override suspend fun beforeSave(sample: Sample): Sample =
                sample.copy(name = "[acme] ${sample.name}")
        }

    @Bean
    @Primary
    fun acmeSampleCachePort(): SampleCachePort =
        object : SampleCachePort {
            override suspend fun get(id: String): Sample? = null
            override suspend fun put(sample: Sample) {
                // Acme 환경에서는 캐시 적재를 수행하지 않는 no-op 동작
            }
        }

    @Bean
    fun acmeRouter(): RouterFunction<ServerResponse> =
        coRouter {
            GET("/api/acme/info") {
                ServerResponse.ok().bodyValueAndAwait(
                    mapOf("customer" to "acme", "status" to "active"),
                )
            }
        }
}
