package cc.midolog.storage.jpa.customer

import cc.midolog.customer.port.repository.CustomerRepositoryPort
import cc.midolog.customer.port.repository.CustomerRepositoryPortContract
import cc.midolog.customers.acme.model.AcmeOrderNote
import cc.midolog.storage.jpa.customers.acme.AcmeOrderNoteJpaMapper
import cc.midolog.storage.jpa.customers.acme.AcmeOrderNoteJpaRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import cc.midolog.storage.jpa.JpaTestApplication

@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@ContextConfiguration(classes = [JpaTestApplication::class])
@EntityScan(basePackages = ["cc.midolog.storage.jpa"])
@EnableJpaRepositories(basePackages = ["cc.midolog.storage.jpa"])
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb_jpa_acme;DB_CLOSE_DELAY=-1"
])
class AcmeJpaCustomerRepositoryPortContractTest : CustomerRepositoryPortContract<AcmeOrderNote, String>() {

    @Autowired
    private lateinit var repository: AcmeOrderNoteJpaRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    override fun port(): CustomerRepositoryPort<AcmeOrderNote, String> {
        return JpaCustomerRepositoryAdapter(
            jpaRepository = repository,
            transactionOperations = transactionTemplate,
            toDomain = AcmeOrderNoteJpaMapper::toDomain,
            toEntity = AcmeOrderNoteJpaMapper::toEntity,
            idOfDomain = { it.id },
        )
    }

    override fun sampleEntity(id: String): AcmeOrderNote =
        AcmeOrderNote(
            id = id,
            customerId = "acme_customer_1",
            note = "Initial note",
            createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
        )

    override fun updatedEntity(entity: AcmeOrderNote): AcmeOrderNote =
        entity.copy(note = "Updated note")

    override fun idOf(entity: AcmeOrderNote): String = entity.id

    override fun newId(): String = "acme_note_${UUID.randomUUID()}"
}
