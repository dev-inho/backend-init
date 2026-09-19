package cc.midolog.storage.jpa.customer

import cc.midolog.customer.port.repository.CustomerRepositoryPort
import cc.midolog.customer.port.repository.CustomerRepositoryPortContract
import cc.midolog.customers.acme.model.AcmeOrderNote
import cc.midolog.storage.jpa.JpaTestApplication
import cc.midolog.storage.jpa.customers.acme.AcmeOrderNoteJpaAutoConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.TestPropertySource
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@ContextConfiguration(classes = [JpaTestApplication::class])
@Import(AcmeOrderNoteJpaAutoConfiguration::class)
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb_jpa_acme;DB_CLOSE_DELAY=-1",
    "storage.persistence.provider=jpa",
    "app.customer=acme",
])
class AcmeJpaCustomerRepositoryPortContractTest : CustomerRepositoryPortContract<AcmeOrderNote, String>() {

    @Autowired
    private lateinit var customerRepositoryPort: CustomerRepositoryPort<AcmeOrderNote, String>

    override fun port(): CustomerRepositoryPort<AcmeOrderNote, String> {
        return customerRepositoryPort
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
