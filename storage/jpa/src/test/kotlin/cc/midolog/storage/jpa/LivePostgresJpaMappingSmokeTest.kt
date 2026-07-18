package cc.midolog.storage.jpa

import cc.midolog.sample.model.RelationChild
import cc.midolog.sample.model.RelationParent
import cc.midolog.sample.model.Sample
import cc.midolog.sample.model.ScalarSample
import cc.midolog.sample.model.ScalarSampleCode
import cc.midolog.sample.model.ScalarSampleStatus
import cc.midolog.storage.jpa.sample.JpaSampleRepositoryAdapter
import cc.midolog.storage.jpa.sample.RelationChildJpaMapper
import cc.midolog.storage.jpa.sample.RelationChildJpaRepository
import cc.midolog.storage.jpa.sample.RelationParentJpaMapper
import cc.midolog.storage.jpa.sample.RelationParentJpaRepository
import cc.midolog.storage.jpa.sample.ScalarSampleJpaMapper
import cc.midolog.storage.jpa.sample.ScalarSampleJpaRepository
import cc.midolog.storage.jpa.user.JpaUserRepositoryAdapter
import cc.midolog.user.model.User
import jakarta.persistence.EntityManager
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
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

@Tag("live-postgres")
@DataJpaTest(
    properties = [
        "spring.datasource.url=\${DB_URL:jdbc:postgresql://localhost:5432/backend}",
        "spring.datasource.username=\${DB_USERNAME:backend}",
        "spring.datasource.password=\${DB_PASSWORD:backend}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.locations=filesystem:../../core/application/src/main/resources/db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.data.jpa.repositories.enabled=true",
    ],
)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("jpa")
@Import(
    JpaSampleRepositoryAdapter::class,
    JpaUserRepositoryAdapter::class,
    LivePostgresJpaMappingSmokeTest.JpaTestConfig::class,
)
class LivePostgresJpaMappingSmokeTest {

    @Autowired
    private lateinit var sampleAdapter: JpaSampleRepositoryAdapter

    @Autowired
    private lateinit var userAdapter: JpaUserRepositoryAdapter

    @Autowired
    private lateinit var scalarRepository: ScalarSampleJpaRepository

    @Autowired
    private lateinit var parentRepository: RelationParentJpaRepository

    @Autowired
    private lateinit var childRepository: RelationChildJpaRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `generated JPA mappings persist against live PostgreSQL`() = runBlocking {
        val sample = Sample(id = "live_sample_1000", name = "live sample")
        val user = User(id = "live_user_1000", email = "live@example.com", displayName = "Live User")
        val scalar = ScalarSample(
            id = "live_scalar_1000",
            displayName = "Live Scalar",
            nickname = null,
            status = ScalarSampleStatus.ACTIVE,
            code = ScalarSampleCode("LIVE-CODE"),
        )
        val parent = RelationParent(id = "live_parent_1000", name = "live parent")
        val child = RelationChild(id = "live_child_1000", parentId = parent.id, name = "live child")

        assertEquals(sample, sampleAdapter.save(sample))
        assertEquals(user, userAdapter.save(user))
        scalarRepository.save(ScalarSampleJpaMapper.toEntity(scalar))
        parentRepository.save(RelationParentJpaMapper.toEntity(parent))
        childRepository.save(RelationChildJpaMapper.toEntity(child))
        entityManager.flush()
        entityManager.clear()

        assertEquals(sample, sampleAdapter.findById(sample.id))
        assertEquals(user, userAdapter.findById(user.id))
        assertEquals(scalar, scalarRepository.findById(scalar.id).map(ScalarSampleJpaMapper::toDomain).orElse(null))
        assertEquals(child, childRepository.findById(child.id).map(RelationChildJpaMapper::toDomain).orElse(null))

        val scalarRow = entityManager
            .createNativeQuery("select display_name, nickname, status, code_value from scalar_sample where id = ?")
            .setParameter(1, scalar.id)
            .singleResult as Array<*>
        assertEquals("Live Scalar", scalarRow[0])
        assertEquals(null, scalarRow[1])
        assertEquals("ACTIVE", scalarRow[2])
        assertEquals("LIVE-CODE", scalarRow[3])

        val parentId = entityManager
            .createNativeQuery("select parent_id from relation_child where id = ?")
            .setParameter(1, child.id)
            .singleResult
        assertEquals(parent.id, parentId)
    }

    @Profile("jpa")
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
