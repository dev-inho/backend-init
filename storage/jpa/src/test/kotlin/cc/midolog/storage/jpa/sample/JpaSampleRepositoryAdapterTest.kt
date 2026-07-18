package cc.midolog.storage.jpa.sample

import cc.midolog.sample.model.Sample
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Profile
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals

@DataJpaTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.jpa.repositories.enabled=true",
    ],
)
@ActiveProfiles("jpa")
@Import(JpaSampleRepositoryAdapter::class, JpaSampleRepositoryAdapterTest.JpaTestConfig::class)
class JpaSampleRepositoryAdapterTest {

    @Autowired
    private lateinit var adapter: JpaSampleRepositoryAdapter

    @Test
    fun `save persists sample and findById restores domain model`() = runBlocking {
        val sample = Sample(id = "sample_jpa_1000", name = "stored")

        assertEquals(sample, adapter.save(sample))
        assertEquals(sample, adapter.findById(sample.id))
    }

    @Profile("jpa")
    @SpringBootConfiguration
    @EntityScan("cc.midolog.storage.jpa.sample")
    @EnableJpaRepositories("cc.midolog.storage.jpa.sample")
    class JpaTestConfig {
        @Bean
        fun jpaTransactionOperations(
            transactionManager: PlatformTransactionManager,
        ): TransactionOperations = TransactionTemplate(transactionManager)
    }
}
