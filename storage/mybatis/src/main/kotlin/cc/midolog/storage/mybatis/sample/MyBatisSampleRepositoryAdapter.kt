package cc.midolog.storage.mybatis.sample

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.storage.mybatis.sample.SampleDynamicSqlSupport.sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mybatis.dynamic.sql.util.kotlin.mybatis3.select

/**
 * 샘플 저장소 포트(SampleRepositoryPort)의 MyBatis 기반 영속성 어댑터.
 *
 * 블로킹 JDBC 호출을 Dispatchers.IO로 격리하여 WebFlux 이벤트루프를 차단하지 않는다.
 * 조회는 생성된 SampleDynamicSqlSupport를 통한 MyBatis Dynamic SQL로 수행하며,
 * 저장은 단일 행 갱신 무결성을 보장하기 위해 affectedRows == 1을 엄격히 검증한다.
 */
class MyBatisSampleRepositoryAdapter(
    private val sampleMapper: SampleMapper,
) : SampleRepositoryPort {

    override suspend fun findById(id: String): Sample? = withContext(Dispatchers.IO) {
        val selectStatement = select(
            SampleDynamicSqlSupport.id,
            SampleDynamicSqlSupport.name,
        ) {
            from(sample)
            where { SampleDynamicSqlSupport.id isEqualTo id }
        }
        val row = sampleMapper.selectOneMappedRow(selectStatement) ?: return@withContext null
        Sample(
            id = (row["id"] ?: row["ID"]) as String,
            name = (row["name"] ?: row["NAME"]) as String,
        )
    }

    override suspend fun save(sample: Sample): Sample = withContext(Dispatchers.IO) {
        val affectedRows = sampleMapper.upsert(sample.id, sample.name)
        check(affectedRows == 1) {
            "Expected to upsert one sample row but affected $affectedRows rows"
        }
        sample
    }
}
