package cc.midolog.storage

import cc.midolog.ApplicationServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Path

/**
 * 호스트 scanBasePackages = ["cc.midolog"]와 FileStorageAutoConfiguration이 함께 로드될 때
 * 레거시 어댑터(cc.midolog.client.storage.LocalFileStorageAdapter)와
 * 신규 로컬 스토리지 자동 설정 빈 간의 이름 충돌 없이 두 포트 구현체가 공존하는지 검증하는 핵심 가드 테스트.
 */
@org.springframework.context.annotation.Import(FileStorageIntegrationTest.TestStubConfig::class)
@SpringBootTest(
    classes = [ApplicationServer::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "jwt.secret=this_is_a_test_secret_for_application_integration_test_at_least_32_bytes",
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
            "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration," +
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration," +
            "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration," +
            "org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration," +
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration",
    ],
)
class FileStorageIntegrationTest {

    @org.springframework.boot.test.context.TestConfiguration
    class TestStubConfig {
        @org.springframework.context.annotation.Bean
        fun fileMetaRepositoryPort(): cc.midolog.file.port.repository.FileMetaRepositoryPort =
            object : cc.midolog.file.port.repository.FileMetaRepositoryPort {
                override suspend fun findById(id: String): cc.midolog.file.model.FileMeta? = null
                override suspend fun save(file: cc.midolog.file.model.FileMeta): cc.midolog.file.model.FileMeta = file
                override suspend fun updateStatus(id: String, status: cc.midolog.file.model.FileStatus): Boolean = true
                override suspend fun findExpiredPending(cutoff: java.time.Instant, limit: Int): List<cc.midolog.file.model.FileMeta> = emptyList()
            }

        @org.springframework.context.annotation.Bean
        fun userRepositoryPort(): cc.midolog.user.port.repository.UserRepositoryPort =
            object : cc.midolog.user.port.repository.UserRepositoryPort {
                override suspend fun findById(id: String): cc.midolog.user.model.User? = null
                override suspend fun save(user: cc.midolog.user.model.User): cc.midolog.user.model.User = user
            }

        @org.springframework.context.annotation.Bean
        fun sampleRepositoryPort(): cc.midolog.sample.port.repository.SampleRepositoryPort =
            object : cc.midolog.sample.port.repository.SampleRepositoryPort {
                override suspend fun findById(id: String): cc.midolog.sample.model.Sample? = null
                override suspend fun save(sample: cc.midolog.sample.model.Sample): cc.midolog.sample.model.Sample = sample
            }
    }

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    companion object {
        @TempDir
        @JvmStatic
        lateinit var tempDir: Path

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("storage.file.provider") { "local" }
            registry.add("storage.file.local.root-dir") { tempDir.toAbsolutePath().toString() }
        }
    }

    @Test
    fun `호스트 패키지 스캔과 FileStorageAutoConfiguration이 함께 로드될 때 두 FileStoragePort 빈이 모두 정상 등록된다`() {
        val legacyPortBeans = applicationContext.getBeansOfType(cc.midolog.sample.port.file.FileStoragePort::class.java)
        assertEquals(1, legacyPortBeans.size, "옛 cc.midolog.sample.port.file.FileStoragePort 빈은 1개여야 한다")

        val newPortBeans = applicationContext.getBeansOfType(cc.midolog.file.port.storage.FileStoragePort::class.java)
        assertEquals(1, newPortBeans.size, "새 cc.midolog.file.port.storage.FileStoragePort 빈은 1개여야 한다")
    }
}
