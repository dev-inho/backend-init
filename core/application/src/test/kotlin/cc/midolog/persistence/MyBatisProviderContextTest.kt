package cc.midolog.persistence

import cc.midolog.ApplicationServer
import cc.midolog.sample.port.repository.SampleRepositoryPort
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.jdbc.Sql
import java.nio.file.Files
import java.util.UUID

@SpringBootTest(
    classes = [ApplicationServer::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:testdb_mybatis;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.flyway.enabled=false",
        "storage.file.provider=local",
        "gateway.mode=embedded",
        "storage.persistence.provider=mybatis"
    ]
)
class MyBatisProviderContextTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.secret") { UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "") }
            registry.add("storage.file.local.root-dir") { Files.createTempDirectory("midolog-test").toAbsolutePath().toString() }
        }
    }

    @Autowired
    private lateinit var context: org.springframework.context.ApplicationContext

    @Autowired
    private lateinit var sampleRepositoryPort: SampleRepositoryPort

    @Test
    @Sql(statements = ["CREATE TABLE IF NOT EXISTS sample (id VARCHAR(255) PRIMARY KEY, name VARCHAR(255) NOT NULL)"])
    fun `마이바티스 프로필이 활성화되면 저장소 포트 빈이 정상 등록되고, 실제 쿼리도 동작한다`() = runTest {
        assertThat(sampleRepositoryPort).isNotNull
        val result = sampleRepositoryPort.findById("coord-probe")
        assertThat(result).isNull()

        val emfClass = Class.forName("jakarta.persistence.EntityManagerFactory")
        val entityManagers = context.getBeanNamesForType(emfClass)
        assertThat(entityManagers).`as`("MyBatis 프로필 활성화 시 다른 기술(JPA) 인프라 빈(EntityManagerFactory)이 0이어야 한다").isEmpty()
    }
}
