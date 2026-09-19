package cc.midolog.customers.acme

import cc.midolog.customer.port.repository.CustomerRepositoryPort
import cc.midolog.customers.acme.model.AcmeOrderNote
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import org.springframework.test.context.jdbc.Sql
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest(classes = [cc.midolog.ApplicationServer::class])
@TestPropertySource(properties = [
    "app.customer=acme",
    "gateway.mode=embedded",
    "storage.persistence.provider=mybatis",
    "spring.datasource.url=jdbc:h2:mem:testdb_acme_port;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.sql.init.mode=never",
    "spring.data.redis.repositories.enabled=false",
    "storage.file.provider=local",
    "storage.file.local.root-dir=/tmp/acme-test-storage",
    "storage.file.max-size-bytes=8192",
    "storage.file.allowed-content-types=image/png,image/jpeg,application/pdf",
    "jwt.secret=this-is-a-dummy-jwt-secret-for-testing-only-must-be-long"
])
@Sql(statements = [
    "DROP TABLE IF EXISTS acme_order_note;",
    "CREATE TABLE acme_order_note (id VARCHAR(255) PRIMARY KEY, customer_id VARCHAR(255) NOT NULL, note VARCHAR(255) NOT NULL, created_at TIMESTAMP NOT NULL);"
])
class AcmeOrderNoteIntegrationTest {

    @Autowired(required = false)
    private var port: CustomerRepositoryPort<AcmeOrderNote, String>? = null

    @Test
    fun `CustomerRepositoryPort is wired and functional in application context`(): Unit = runBlocking {
        assertThat(port).isNotNull

        val id = "note_${UUID.randomUUID()}"
        val note = AcmeOrderNote(
            id = id,
            customerId = "acme",
            note = "Hello Acme",
            createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
        )

        val saved = port!!.save(note)
        assertThat(saved).isEqualTo(note)

        val loaded = port!!.findById(id)
        assertThat(loaded).isEqualTo(note)

        val deleted = port!!.deleteById(id)
        assertThat(deleted).isTrue()

        val afterDelete = port!!.findById(id)
        assertThat(afterDelete).isNull()
    }
}
