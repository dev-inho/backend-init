package cc.midolog.persistence

import cc.midolog.ApplicationServer
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import java.nio.file.Files
import java.util.UUID

/**
 * MyBatis Dynamic SQL 매퍼 및 어댑터가 XML 없이 기본 자동 구성을 통해 정상 바인딩되고
 * 런타임 쿼리를 성공적으로 실행함을 실호출로 검증하는 영속성 바인딩 가드 테스트.
 */
@SpringBootTest(
    classes = [ApplicationServer::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:testdb_mybatis_guard;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false",
        "storage.file.provider=local",
        "gateway.mode=embedded",
        "storage.persistence.provider=mybatis"
    ]
)
class MyBatisMapperBindingGuardTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.secret") { UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "") }
            registry.add("storage.file.local.root-dir") { Files.createTempDirectory("guard-test").toAbsolutePath().toString() }
        }
    }

    @Autowired
    private lateinit var sampleRepositoryPort: SampleRepositoryPort

    @Test
    @Sql(statements = ["CREATE TABLE IF NOT EXISTS sample (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255) NOT NULL)"])
    fun `guard - mybatis dynamic sql mapper should be bound by default auto-configuration and execute probe query`() {
        runBlocking {
            sampleRepositoryPort.findById("guard-probe")
        }
    }
}
