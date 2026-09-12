package cc.midolog.storage.jpa.file

import cc.midolog.file.port.repository.FileMetaRepositoryPort
import cc.midolog.file.port.repository.FileMetaRepositoryPortContract
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest
import org.springframework.boot.persistence.autoconfigure.EntityScan
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

@DataJpaTest(properties = ["spring.jpa.hibernate.ddl-auto=create-drop"])
@EntityScan(basePackages = ["cc.midolog.storage.jpa"])
@TestPropertySource(properties = [
    "spring.datasource.url=jdbc:h2:mem:testdb_jpa_filemeta;DB_CLOSE_DELAY=-1"
])
class JpaFileMetaRepositoryPortContractTest : FileMetaRepositoryPortContract() {

    @Autowired
    private lateinit var repository: FileMetaJpaRepository

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var entityManager: jakarta.persistence.EntityManager

    private var testClock: Clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneId.of("UTC"))

    override fun port(): FileMetaRepositoryPort {
        // GUARD: Adapter is instantiated without Clock (current main behavior)
        return JpaFileMetaRepositoryAdapter(repository, transactionTemplate, entityManager)
    }

    override fun clock(): Clock {
        return testClock
    }
}
