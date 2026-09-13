package cc.midolog.infra.cache

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.cache.SampleCachePort
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/**
 * Redis 기반으로 동작하는 [SampleCachePort] 도메인 포트 구현체 어댑터.
 *
 * 특정 외부 인프라스트럭처(Redis)에 의존하는 어댑터 클래스이므로 S3 아키텍처 규칙에 따라 `core/application` 내의 `infra/cache` 패키지에 위치한다.
 * 캐시 엔트리는 10분([Duration.ofMinutes(10)])의 고정 TTL을 가지며, 저장 데이터는 단순 파이프 문자열(`id|name`)로 결합되어 직렬화된다.
 * 고객 전용 모듈에서 자체 [SampleCachePort] 빈을 등록할 경우 본 어댑터가 물러나도록 [@ConditionalOnMissingBean]을 적용한다.
 */
@Component
class RedisSampleCacheAdapter(
    private val redis: ReactiveStringRedisTemplate,
) : SampleCachePort {
    private val ttl = Duration.ofMinutes(10)
    private fun key(id: String) = "sample:$id"
    private val SEP = "|"

    /**
     * 캐시 키에서 파이프 구분자 포맷의 문자열을 조회하여 [Sample] 도메인 모델로 역직렬화한다.
     *
     * 키가 존재하지 않거나 역직렬화 포맷 규격(2개 조각)에 맞지 않으면 null을 반환한다.
     */
    override suspend fun get(id: String): Sample? {
        val raw = redis.opsForValue().get(key(id)).awaitSingleOrNull() ?: return null
        val parts = raw.split(SEP, limit = 2)
        return if (parts.size == 2) Sample(id = parts[0], name = parts[1]) else null
    }

    /**
     * [Sample] 데이터를 파이프 문자열로 직렬화하여 10분 만료 시간과 함께 Redis에 적재한다.
     */
    override suspend fun put(sample: Sample) {
        redis.opsForValue()
            .set(key(sample.id), "${sample.id}$SEP${sample.name}", ttl)
            .awaitSingleOrNull()
    }
}
