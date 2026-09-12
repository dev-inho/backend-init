package cc.midolog.infra.cache

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.cache.SampleCachePort
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/** Redis 기반 Sample 캐시 어댑터(reactive + 코루틴 브리지). */
@Component
class RedisSampleCacheAdapter(
    private val redis: ReactiveStringRedisTemplate,
) : SampleCachePort {
    private val ttl = Duration.ofMinutes(10)
    private fun key(id: String) = "sample:$id"
    private val SEP = "|"

    override suspend fun get(id: String): Sample? {
        val raw = redis.opsForValue().get(key(id)).awaitSingleOrNull() ?: return null
        val parts = raw.split(SEP, limit = 2)
        return if (parts.size == 2) Sample(id = parts[0], name = parts[1]) else null
    }

    override suspend fun put(sample: Sample) {
        redis.opsForValue()
            .set(key(sample.id), "${sample.id}$SEP${sample.name}", ttl)
            .awaitSingleOrNull()
    }
}
