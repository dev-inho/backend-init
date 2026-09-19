package cc.midolog.storage.mybatis.customer

import cc.midolog.customer.port.repository.CustomerRepositoryPort
import cc.midolog.customer.port.repository.CustomerRepositoryPortContract
import cc.midolog.customers.acme.model.AcmeOrderNote
import cc.midolog.storage.mybatis.autoconfigure.MyBatisStorageAutoConfiguration
import cc.midolog.storage.mybatis.customers.acme.AcmeOrderNoteMyBatisAutoConfiguration
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@MybatisTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(MyBatisStorageAutoConfiguration::class, AcmeOrderNoteMyBatisAutoConfiguration::class)
@TestPropertySource(properties = [
    "storage.persistence.provider=mybatis",
    "app.customer=acme",
    "mybatis.mapper-locations=classpath*:mapper/**/*.xml",
    "spring.datasource.url=jdbc:h2:mem:testdb_mybatis_acme;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driverClassName=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
])
@Sql(statements = [
    "DROP TABLE IF EXISTS acme_order_note;",
    "CREATE TABLE acme_order_note (id VARCHAR(255) PRIMARY KEY, customer_id VARCHAR(255) NOT NULL, note VARCHAR(255) NOT NULL, created_at TIMESTAMP NOT NULL);"
])
class AcmeMyBatisCustomerRepositoryPortContractTest : CustomerRepositoryPortContract<AcmeOrderNote, String>() {

    @Autowired
    private lateinit var customerRepositoryPort: CustomerRepositoryPort<AcmeOrderNote, String>

    override fun port(): CustomerRepositoryPort<AcmeOrderNote, String> = customerRepositoryPort

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
