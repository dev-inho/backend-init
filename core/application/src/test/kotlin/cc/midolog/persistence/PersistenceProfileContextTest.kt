package cc.midolog.persistence

import cc.midolog.ApplicationServer
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.nio.file.Files
import java.util.UUID

@SpringBootTest(
    classes = [ApplicationServer::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:testdb_jpa;DB_CLOSE_DELAY=-1",
        "spring.flyway.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "storage.file.provider=local",
        "gateway.mode=embedded"
    ]
)
@ActiveProfiles("jpa")
class PersistenceProfileContextTest {

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("jwt.secret") { UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "") }
            registry.add("storage.file.local.root-dir") { Files.createTempDirectory("midolog-test").toAbsolutePath().toString() }
        }
    }

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `jpa profile registers exactly one of each port adapter`() {
        val sampleBeans = context.getBeansOfType(SampleRepositoryPort::class.java).values.filter { !it.javaClass.name.contains("TestStubConfig") }
        val userBeans = context.getBeansOfType(UserRepositoryPort::class.java).values.filter { !it.javaClass.name.contains("TestStubConfig") }
        val fileBeans = context.getBeansOfType(FileMetaRepositoryPort::class.java).values.filter { !it.javaClass.name.contains("TestStubConfig") }

        assertEquals(1, sampleBeans.size)
        assertEquals(1, userBeans.size)
        assertEquals(1, fileBeans.size)

        assertEquals("JpaFileMetaRepositoryAdapter", AopUtils.getTargetClass(fileBeans.first()).simpleName)
    }
}
