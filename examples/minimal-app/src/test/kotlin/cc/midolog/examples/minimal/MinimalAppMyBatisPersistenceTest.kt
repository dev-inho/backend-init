package cc.midolog.examples.minimal

import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.jdbc.Sql

/**
 * storage.persistence.provider=mybatis 프로퍼티를 통한 MyBatis 자동 구성 및 포트 동작 실증 테스트.
 *
 * 소비자는 매퍼 XML 위치를 오버라이드하지 않고 스타터의 기본 매퍼(main XML) 바인딩을 그대로 사용하며,
 * 도메인 포트([SampleRepositoryPort]) 인터페이스를 통해 H2 DB 상에서 조회 동작(findById -> null)이 정상 동작함을 검증한다.
 */
@SpringBootTest(
    classes = [MinimalApplication::class],
    properties = [
        "storage.persistence.provider=mybatis",
        "spring.datasource.url=jdbc:h2:mem:minimal_mybatis;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.flyway.enabled=false",
    ]
)
class MinimalAppMyBatisPersistenceTest {

    @Autowired
    private lateinit var sampleRepositoryPort: SampleRepositoryPort

    @Test
    @Sql(statements = ["CREATE TABLE IF NOT EXISTS sample (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255) NOT NULL)"])
    fun `mybatis provider binds domain port and findById returns null on empty table`() = runTest {
        assertNotNull(sampleRepositoryPort)

        val found = sampleRepositoryPort.findById("sample-mybatis-1")
        assertNull(found)
    }
}
