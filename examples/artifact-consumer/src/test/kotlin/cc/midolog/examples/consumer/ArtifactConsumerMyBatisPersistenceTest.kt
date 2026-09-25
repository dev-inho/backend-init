package cc.midolog.examples.consumer

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.jdbc.Sql

/**
 * 독립 소비자 빌드에서 storage.persistence.provider=mybatis 프로퍼티를 통한 MyBatis 자동 구성 및 포트 동작 검증 테스트.
 *
 * 외부 아티팩트 좌표로 주입된 도메인 포트([SampleRepositoryPort])를 활용하여
 * H2 인메모리 환경에서 도메인 모델([Sample])의 save 및 findById 왕복 동작을 검증한다.
 */
@SpringBootTest(
    classes = [ArtifactConsumerApplication::class],
    properties = [
        "storage.persistence.provider=mybatis",
        "spring.datasource.url=jdbc:h2:mem:consumer_mybatis;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.flyway.enabled=false",
        "mybatis.mapper-locations=classpath*:mapper/**/*.xml",
    ]
)
@Sql(statements = [
    "DROP TABLE IF EXISTS sample;",
    "CREATE TABLE sample (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255) NOT NULL);"
])
class ArtifactConsumerMyBatisPersistenceTest {

    @Autowired
    private lateinit var sampleRepositoryPort: SampleRepositoryPort

    @Test
    fun `mybatis provider roundtrip save and findById works via published domain port`() = runTest {
        val sample = Sample(id = "consumer-mybatis-1", name = "Artifact Consumer MyBatis Sample")
        val saved = sampleRepositoryPort.save(sample)
        assertEquals(sample, saved)

        val found = sampleRepositoryPort.findById("consumer-mybatis-1")
        assertEquals(sample, found)
    }
}
