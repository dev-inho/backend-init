package cc.midolog.storage.jpa.jpadsl.fixture

import cc.midolog.jpadsl.fixture.ScalarSample
import cc.midolog.jpadsl.fixture.ScalarSampleCode
import cc.midolog.jpadsl.fixture.ScalarSampleStatus
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionTemplate
import jakarta.persistence.EntityManager
import kotlin.test.assertEquals

@DataJpaTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.jpa.repositories.enabled=true",
    ],
)
class ScalarSampleJpaGenerationTest {

    @Autowired
    private lateinit var repository: ScalarSampleJpaRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `generated mapper preserves nullable enum value object and column override`() = runBlocking {
        val sample = ScalarSample(
            id = "scalar_1000",
            displayName = "Visible Name",
            nickname = null,
            status = ScalarSampleStatus.ACTIVE,
            code = ScalarSampleCode("CODE-1000"),
        )

        val saved = repository.save(ScalarSampleJpaMapper.toEntity(sample))
        entityManager.flush()
        entityManager.clear()

        assertEquals(sample, ScalarSampleJpaMapper.toDomain(saved))
        assertEquals(sample, repository.findById(sample.id).map(ScalarSampleJpaMapper::toDomain).orElse(null))

        val row = entityManager
            .createNativeQuery("select display_name, nickname, status, code_value from scalar_sample where id = ?")
            .setParameter(1, sample.id)
            .singleResult as Array<*>

        assertEquals("Visible Name", row[0])
        assertEquals(null, row[1])
        assertEquals("ACTIVE", row[2])
        assertEquals("CODE-1000", row[3])
    }

    @SpringBootConfiguration
    @EntityScan("cc.midolog.storage.jpa")
    @EnableJpaRepositories("cc.midolog.storage.jpa")
    class JpaTestConfig {
        @Bean
        fun jpaTransactionOperations(
            transactionManager: PlatformTransactionManager,
        ): TransactionOperations = TransactionTemplate(transactionManager)
    }
}
