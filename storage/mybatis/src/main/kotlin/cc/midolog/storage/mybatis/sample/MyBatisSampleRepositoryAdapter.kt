package cc.midolog.storage.mybatis.sample

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository

/**
 * repository 포트의 storage 어댑터.
 * 블로킹 JDBC(MyBatis) 호출을 Dispatchers.IO로 분리해 이벤트루프를 막지 않는다.
 */
@Profile("mybatis")
@Repository
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
