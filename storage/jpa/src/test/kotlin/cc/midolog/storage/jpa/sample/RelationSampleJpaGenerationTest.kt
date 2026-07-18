package cc.midolog.storage.jpa.sample

import cc.midolog.sample.model.RelationChild
import cc.midolog.sample.model.RelationParent
import jakarta.persistence.EntityManager
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.context.annotation.Bean
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionOperations
import org.springframework.transaction.support.TransactionTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DataJpaTest(
    properties = [
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.data.jpa.repositories.enabled=true",
    ],
)
@ActiveProfiles("jpa")
class RelationSampleJpaGenerationTest {

    @Autowired
    private lateinit var parentRepository: RelationParentJpaRepository

    @Autowired
    private lateinit var childRepository: RelationChildJpaRepository

    @Autowired
    private lateinit var entityManager: EntityManager

    @Test
    fun `generated relation entity annotations use lazy parent child mapping`() {
        val parentChildren = RelationParentJpaEntity::class.java.getDeclaredField("children")
        val oneToMany = assertNotNull(parentChildren.getAnnotation(OneToMany::class.java))
        assertEquals("parent", oneToMany.mappedBy)
        assertEquals(FetchType.LAZY, oneToMany.fetch)

        val childParent = RelationChildJpaEntity::class.java.getDeclaredField("parent")
        val manyToOne = assertNotNull(childParent.getAnnotation(ManyToOne::class.java))
        val joinColumn = assertNotNull(childParent.getAnnotation(JoinColumn::class.java))
        assertEquals(FetchType.LAZY, manyToOne.fetch)
        assertEquals("parent_id", joinColumn.name)
        assertEquals("id", joinColumn.referencedColumnName)
    }

    @Test
    fun `generated relation mapper persists parent child without recursive domain graph`() = runBlocking {
        val parent = RelationParent(id = "parent_1000", name = "parent")
        val child = RelationChild(id = "child_1000", parentId = parent.id, name = "child")

        parentRepository.save(RelationParentJpaMapper.toEntity(parent))
        childRepository.save(RelationChildJpaMapper.toEntity(child))
        entityManager.flush()
        entityManager.clear()

        val foundChild = childRepository.findById(child.id).orElseThrow()
        assertEquals(child, RelationChildJpaMapper.toDomain(foundChild))

        val foundParent = parentRepository.findById(parent.id).orElseThrow()
        val mappedParent = RelationParentJpaMapper.toDomain(foundParent)
        assertEquals(parent.copy(children = emptyList()), mappedParent)
        assertTrue(mappedParent.children.isEmpty())

        val parentId = entityManager
            .createNativeQuery("select parent_id from relation_child where id = ?")
            .setParameter(1, child.id)
            .singleResult

        assertEquals(parent.id, parentId)
    }

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
