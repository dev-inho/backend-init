package cc.midolog.persistence

import cc.midolog.ApplicationServer
import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.sample.port.repository.SampleRepositoryPort
import cc.midolog.user.port.repository.UserRepositoryPort
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest(
    classes = [ApplicationServer::class],
    properties = [
        "spring.datasource.url=jdbc:h2:mem:testdb_mybatis;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
        "spring.flyway.enabled=false",
        "spring.main.allow-bean-definition-overriding=true",
        "storage.file.provider=local",
        "storage.file.local.root-dir=/tmp",
        "gateway.mode=embedded",
        "jwt.secret=12345678901234567890123456789012"
    ]
)
@ActiveProfiles("mybatis")
class MyBatisProfileContextTest {

    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `mybatis profile active registers only mybatis port adapters`() {
        val sampleBeans = context.getBeansOfType(SampleRepositoryPort::class.java).values.filter { !it.javaClass.name.contains("TestStubConfig") }
        val userBeans = context.getBeansOfType(UserRepositoryPort::class.java).values.filter { !it.javaClass.name.contains("TestStubConfig") }
        val fileBeans = context.getBeansOfType(FileMetaRepositoryPort::class.java).values.filter { !it.javaClass.name.contains("TestStubConfig") }

        assertEquals(1, sampleBeans.size)
        assertEquals(1, userBeans.size)
        assertEquals(1, fileBeans.size)

        assertEquals("MyBatisSampleRepositoryAdapter", sampleBeans.first()?.let { org.springframework.aop.support.AopUtils.getTargetClass(it).simpleName })
        assertEquals("MyBatisUserRepositoryAdapter", userBeans.first()?.let { org.springframework.aop.support.AopUtils.getTargetClass(it).simpleName })
        assertEquals("MyBatisFileMetaRepositoryAdapter", fileBeans.first()?.let { org.springframework.aop.support.AopUtils.getTargetClass(it).simpleName })
    }
}
