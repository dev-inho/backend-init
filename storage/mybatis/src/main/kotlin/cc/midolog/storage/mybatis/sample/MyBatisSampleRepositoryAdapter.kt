package cc.midolog.storage.mybatis.sample

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 샘플 저장소 포트(SampleRepositoryPort)의 MyBatis 기반 영속성 어댑터.
 *
 * 블로킹 JDBC(MyBatis) 호출을 Dispatchers.IO로 분리해 WebFlux 이벤트루프를 막지 않는다.
 * save 시 upsert 영향 행 수가 정확히 1건인지 check(affectedRows == 1)로 검증하여
 * 동시성 경합이나 부분 실패 발생 시 즉시 예외를 던진다.
 */
class MyBatisSampleRepositoryAdapter(
    private val sampleMapper: SampleMapper,
) : SampleRepositoryPort {
    override suspend fun findById(id: String): Sample? = withContext(Dispatchers.IO) {
        sampleMapper.selectById(id)?.let {
            Sample(id = it["id"] as String, name = it["name"] as String)
        }
    }

    override suspend fun save(sample: Sample): Sample = withContext(Dispatchers.IO) {
        val affectedRows = sampleMapper.upsert(sample.id, sample.name)
        check(affectedRows == 1) {
            "Expected to upsert one sample row but affected $affectedRows rows"
        }
        sample
    }
}
