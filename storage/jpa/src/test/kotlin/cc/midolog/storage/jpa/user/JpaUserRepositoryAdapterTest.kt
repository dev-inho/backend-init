package cc.midolog.storage.jpa.user

import cc.midolog.user.model.User
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
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
@Import(JpaUserRepositoryAdapter::class, JpaUserRepositoryAdapterTest.JpaTestConfig::class)
class JpaUserRepositoryAdapterTest {

    @Autowired
    private lateinit var adapter: JpaUserRepositoryAdapter

    @Test
    fun `save persists user and findById restores domain model`() = runBlocking {
        val user = User(id = "user_jpa_1000", email = "stored@example.com", displayName = "Stored")

        assertEquals(user, adapter.save(user))
        assertEquals(user, adapter.findById(user.id))
    }

    @SpringBootConfiguration
    @EntityScan("cc.midolog.storage.jpa.user")
    @EnableJpaRepositories("cc.midolog.storage.jpa.user")
    class JpaTestConfig {
        @Bean
        fun jpaTransactionOperations(
            transactionManager: PlatformTransactionManager,
        ): TransactionOperations = TransactionTemplate(transactionManager)
    }
}
