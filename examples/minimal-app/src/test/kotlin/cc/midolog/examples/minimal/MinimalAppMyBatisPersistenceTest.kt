package cc.midolog.examples.minimal

import cc.midolog.sample.model.Sample
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.jdbc.Sql

/**
 * storage.persistence.provider=mybatis 프로퍼티를 통한 MyBatis 자동 구성 및 포트 동작 실증 테스트.
 *
 * 소비자 애플리케이션의 테스트 전용 H2 매퍼 XML 위치(mapper-h2)를 프로퍼티로 오버라이드하고,
 * 도메인 포트([SampleRepositoryPort]) 인터페이스를 통해 H2 DB 상에서 엔티티 왕복 저장이 정상 동작함을 검증한다.
 */
@SpringBootTest(
    classes = [MinimalApplication::class],
    properties = [
        "storage.persistence.provider=mybatis",
        "spring.datasource.url=jdbc:h2:mem:minimal_mybatis;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.flyway.enabled=false",
        "mybatis.mapper-locations=classpath*:mapper-h2/**/*.xml",
    ]
)
class MinimalAppMyBatisPersistenceTest {

    @Autowired
    private lateinit var sampleRepositoryPort: SampleRepositoryPort

    @Test
    @Sql(statements = ["CREATE TABLE IF NOT EXISTS sample (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255) NOT NULL)"])
    fun `mybatis provider roundtrip save and findById works via domain port`() = runTest {
        val sample = Sample(id = "sample-mybatis-1", name = "Minimal MyBatis Sample")
        val saved = sampleRepositoryPort.save(sample)
        assertEquals(sample, saved)

        val found = sampleRepositoryPort.findById("sample-mybatis-1")
        assertEquals(sample, found)
    }
}
